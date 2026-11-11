/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive.archiver

import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java8.nio.file.DirectoryNotEmptyException
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.Files
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.NotDirectoryException
import java8.nio.file.Path
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributeView
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.archive.toFileSystemOrInterruptedIOException
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.createDirectory
import me.zhanghai.android.files.provider.common.createSymbolicLink
import me.zhanghai.android.files.provider.common.deleteIfExists
import me.zhanghai.android.files.provider.common.exists
import me.zhanghai.android.files.provider.common.newOutputStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.provider.common.resolveForeign
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.common.toInt
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.linux.syscall.Constants
import me.zhanghai.android.files.provider.linux.syscall.StructTimespec
import me.zhanghai.android.files.provider.linux.syscall.Syscall
import me.zhanghai.android.files.provider.linux.syscall.SyscallException
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.provider.root.RootablePath
import me.zhanghai.android.files.provider.root.isRunningAsRoot
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.FileDescriptor
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * Extracts entries of an archive in a single pass over it.
 *
 * The calling thread decodes the archive in stored order. Small regular files are buffered and
 * written by a bounded pool of writer threads, while directories, symbolic links and large files
 * are handled on the calling thread.
 */
class ArchiveExtractor(
    private val archiveFile: Path,
    private val passwords: () -> List<String>,
    private val options: Options = Options()
) {
    data class Options(
        val writerThreads: Int = min(4, Runtime.getRuntime().availableProcessors()),
        val smallFileThreshold: Long = 1L shl 20,
        val maxBufferedBytes: Long = 32L shl 20
    )

    /**
     * Entries at or under [source] (a path in this archive's ArchiveFileSystem, possibly its root)
     * are extracted under [target].
     */
    class Root(val source: Path, val target: Path)

    sealed class ConflictDecision {
        object Replace : ConflictDecision()
        /** Directories only; treated as [Replace] for other entries. */
        object Merge : ConflictDecision()
        object Skip : ConflictDecision()
        class Rename(val target: Path) : ConflictDecision()
        object Cancel : ConflictDecision()
    }

    enum class ErrorDecision { RETRY, SKIP, CANCEL }

    /** ALL callbacks are invoked on the thread that called [extract]. */
    interface Callback {
        fun onConflict(source: Path, target: Path): ConflictDecision

        fun onError(source: Path, target: Path, exception: IOException): ErrorDecision

        /**
         * [bytes] = newly written bytes since last call (may be 0), [files] = newly completed
         * entries.
         */
        fun onProgress(currentSource: Path, bytes: Long, files: Int)
    }

    /**
     * @throws InterruptedIOException if the calling thread is interrupted, or if a callback decided
     * to cancel
     */
    @Throws(IOException::class)
    fun extract(roots: List<Root>, callback: Callback) {
        if (roots.isEmpty()) {
            return
        }
        Session(roots, callback).run()
    }

    private inner class Session(private val roots: List<Root>, private val callback: Callback) {
        private val rootPath: Path = roots.first().source.root
            ?: throw IllegalArgumentException("Source ${roots.first().source} must be absolute")

        init {
            for (root in roots) {
                require(root.source.root == rootPath) {
                    "Source ${root.source} must be in the same archive as ${roots.first().source}"
                }
            }
        }

        private val tree = ExtractionTree(roots)

        private val charset = ArchiveReader.archiveFileNameCharset

        // Remote and document providers aren't necessarily safe or faster with concurrent writes.
        private val writerThreadCount = if (roots.all { it.target.isLinuxPath }) {
            options.writerThreads.coerceAtLeast(1)
        } else {
            1
        }

        private val smallFileThreshold = options.smallFileThreshold
            .coerceAtMost(MAX_SMALL_FILE_SIZE.toLong())

        private var pool: ThreadPoolExecutor? = null

        private val lock = ReentrantLock()

        private val condition = lock.newCondition()

        // Guarded by lock.
        private val results = ArrayDeque<WriteResult>()

        // Guarded by lock.
        private var inFlightTaskCount = 0

        // Only accessed on the calling thread, since writers never release bytes themselves.
        private val budget = ByteBudget(options.maxBufferedBytes)

        private val directoryTimes = mutableListOf<Pair<Path, FileTime>>()

        private val scanPaths = mutableListOf<String>()

        private val localTargetCache = HashMap<Path, Boolean>()

        private var directBuffer: ByteBuffer? = null

        private var heapBuffer: ByteBuffer? = null

        @Throws(IOException::class)
        fun run() {
            var successful = false
            try {
                var startIndex: Int? = 0
                while (startIndex != null) {
                    startIndex = runPass(startIndex)
                }
                createMissingRoots()
                awaitAllWrites()
                setDirectoryTimes()
                successful = true
            } finally {
                shutDownPool(successful)
                scanMedia()
            }
        }

        /**
         * @return the entry index to resume from after reopening the archive, or `null` if done
         */
        @Throws(IOException::class)
        private fun runPass(startIndex: Int): Int? {
            val (archive, closeable) = try {
                ArchiveReader.openArchive(archiveFile, passwords(), true, ARCHIVE_BUFFER_SIZE)
            } catch (e: IOException) {
                return when (handleArchiveException(e, rootPath, null)) {
                    ErrorDecision.RETRY -> startIndex
                    ErrorDecision.SKIP -> null
                    ErrorDecision.CANCEL -> throw InterruptedIOException()
                }
            }
            closeable.use {
                var index = 0
                var currentPath: Path? = null
                var currentTarget: Path? = null
                try {
                    while (true) {
                        throwIfInterrupted()
                        handleResults(pollResults())
                        currentPath = null
                        currentTarget = null
                        val entry = archive.readEntry(charset) ?: return null
                        val entryIndex = index++
                        if (entryIndex < startIndex) {
                            continue
                        }
                        val path = ArchiveReader.normalizeEntryPath(
                            rootPath, entry.name, entry.isDirectory
                        ) ?: continue
                        when (val resolution = tree.resolve(path, entryIndex, entry.isDirectory)) {
                            is ExtractionTree.Resolution.NotSelected,
                            is ExtractionTree.Resolution.Unreachable,
                            is ExtractionTree.Resolution.Duplicate -> continue
                            is ExtractionTree.Resolution.LateDirectory -> {
                                val lastModifiedTime = entry.lastModifiedTimeOrNull
                                if (resolution.isCreated && lastModifiedTime != null) {
                                    directoryTimes += resolution.target to lastModifiedTime
                                }
                            }
                            is ExtractionTree.Resolution.Pending -> {
                                currentPath = path
                                if (!createMissingAncestors(resolution)) {
                                    continue
                                }
                                currentTarget = tree.getTarget(path, resolution.root)
                                extractEntry(archive, entry, entryIndex, path, resolution.root)
                            }
                        }
                    }
                } catch (e: ReopenException) {
                    return e.index
                } catch (e: ArchiveException) {
                    val path = currentPath
                    return when (handleArchiveException(e, path ?: rootPath, currentTarget)) {
                        // The index was incremented once the header was read.
                        ErrorDecision.RETRY -> if (path != null) index - 1 else index
                        ErrorDecision.SKIP -> if (path != null) {
                            tree.markSkipped(path, index - 1)
                            index
                        } else {
                            // There's no way to read past a broken header.
                            null
                        }
                        ErrorDecision.CANCEL -> throw InterruptedIOException()
                    }
                }
            }
        }

        @Throws(IOException::class)
        private fun handleArchiveException(
            exception: IOException,
            source: Path,
            target: Path?
        ): ErrorDecision {
            val fileSystemException = if (exception is ArchiveException) {
                exception.toFileSystemOrInterruptedIOException(source)
            } else {
                exception
            }
            if (fileSystemException is InterruptedIOException || Thread.interrupted()) {
                throw InterruptedIOException().apply { initCause(exception) }
            }
            exception.printStackTrace()
            return callback.onError(source, target ?: roots.first().target, fileSystemException)
        }

        @Throws(IOException::class)
        private fun createMissingAncestors(resolution: ExtractionTree.Resolution.Pending): Boolean {
            for (ancestor in resolution.missingAncestors) {
                if (!createDirectory(ancestor, resolution.root, ExtractionTree.SYNTHETIC_INDEX,
                        null, 0)) {
                    return false
                }
            }
            return true
        }

        @Throws(IOException::class)
        private fun extractEntry(
            archive: ReadArchive,
            entry: ReadArchive.Entry,
            index: Int,
            path: Path,
            root: Root
        ) {
            when (entry.type) {
                PosixFileType.DIRECTORY ->
                    createDirectory(path, root, index, entry.lastModifiedTimeOrNull, entry.size)
                PosixFileType.REGULAR_FILE ->
                    if (entry.size in 0..smallFileThreshold) {
                        extractSmallFile(archive, entry, index, path, root)
                    } else {
                        extractLargeFile(archive, entry, index, path, root, emptyList())
                    }
                PosixFileType.SYMBOLIC_LINK -> createSymbolicLink(entry, index, path, root)
                else -> {
                    val target = tree.getTarget(path, root)
                    val exception = IOException("Cannot copy special file to foreign provider")
                    while (resolveError(path, target, exception)) {
                        throwIfInterrupted()
                    }
                    tree.markSkipped(path, index)
                }
            }
        }

        @Throws(IOException::class)
        private fun createDirectory(
            path: Path,
            root: Root,
            index: Int,
            lastModifiedTime: FileTime?,
            size: Long
        ): Boolean {
            var target = tree.getTarget(path, root)
            var mayExist = !tree.isInCreatedDirectory(path, root)
            while (true) {
                throwIfInterrupted()
                try {
                    if (mayExist) {
                        checkForeignTargetAbsent(path, target)
                    }
                    target.createDirectory()
                    tree.markDirectory(path, index, target, true)
                    if (lastModifiedTime != null) {
                        directoryTimes += target to lastModifiedTime
                    }
                    callback.onProgress(path, size, 1)
                    return true
                } catch (e: FileAlreadyExistsException) {
                    mayExist = true
                    when (val decision = callback.onConflict(path, target)) {
                        is ConflictDecision.Merge -> {
                            if (!isDirectoryNoFollowLinks(target)) {
                                val exception = NotDirectoryException(target.toString())
                                if (resolveError(path, target, exception)) {
                                    continue
                                }
                                tree.markSkipped(path, index)
                                return false
                            }
                            tree.markDirectory(path, index, target, false)
                            callback.onProgress(path, size, 1)
                            return true
                        }
                        is ConflictDecision.Replace -> {
                            awaitIdle()
                            try {
                                target.deleteIfExists()
                            } catch (e2: DirectoryNotEmptyException) {
                                // Replacing a directory with a directory is merging.
                                tree.markDirectory(path, index, target, false)
                                callback.onProgress(path, size, 1)
                                return true
                            } catch (e2: InterruptedIOException) {
                                throw e2
                            } catch (e2: IOException) {
                                if (resolveError(path, target, e2)) {
                                    continue
                                }
                                tree.markSkipped(path, index)
                                return false
                            }
                        }
                        is ConflictDecision.Rename -> target = decision.target
                        is ConflictDecision.Skip -> {
                            tree.markSkipped(path, index)
                            return false
                        }
                        is ConflictDecision.Cancel -> throw InterruptedIOException()
                    }
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    e.printStackTrace()
                    mayExist = true
                    if (resolveError(path, target, e)) {
                        continue
                    }
                    tree.markSkipped(path, index)
                    return false
                } catch (e: UnsupportedOperationException) {
                    mayExist = true
                    if (resolveError(path, target, IOException(e))) {
                        continue
                    }
                    tree.markSkipped(path, index)
                    return false
                }
            }
        }

        @Throws(IOException::class)
        private fun createSymbolicLink(
            entry: ReadArchive.Entry,
            index: Int,
            path: Path,
            root: Root
        ) {
            var target = tree.getTarget(path, root)
            val linkTarget = entry.symbolicLinkTarget.orEmpty().toByteString()
            var mayExist = !tree.isInCreatedDirectory(path, root)
            while (true) {
                throwIfInterrupted()
                try {
                    if (mayExist) {
                        checkForeignTargetAbsent(path, target)
                    }
                    target.createSymbolicLink(linkTarget)
                    break
                } catch (e: FileAlreadyExistsException) {
                    mayExist = true
                    target = resolveNonDirectoryConflict(path, target) ?: run {
                        tree.markSkipped(path, index)
                        return
                    }
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    e.printStackTrace()
                    mayExist = true
                    if (!resolveError(path, target, e)) {
                        tree.markSkipped(path, index)
                        return
                    }
                } catch (e: UnsupportedOperationException) {
                    if (!resolveError(path, target, IOException(e))) {
                        tree.markSkipped(path, index)
                        return
                    }
                }
            }
            // Never follow the link we just created, which may point anywhere.
            setLastModifiedTime(target, entry.lastModifiedTimeOrNull, false, true)
            tree.markNonDirectory(path, index)
            callback.onProgress(path, entry.size, 1)
        }

        @Throws(IOException::class)
        private fun extractSmallFile(
            archive: ReadArchive,
            entry: ReadArchive.Entry,
            index: Int,
            path: Path,
            root: Root
        ) {
            val target = tree.getTarget(path, root)
            val isLocal = isLocalTarget(target)
            val size = entry.size.toInt()
            val cost = size.toLong() + TASK_OVERHEAD_BYTES
            acquireBudget(cost)
            var isBudgetTransferred = false
            try {
                val data = ByteArray(size)
                val dataBuffer = ByteBuffer.wrap(data)
                while (dataBuffer.hasRemaining()) {
                    if (archive.readData(dataBuffer) == 0) {
                        break
                    }
                }
                // Also lets libarchive reach the end of the entry and verify its checksum.
                val probeBuffer = getInlineBuffer(isLocal)
                probeBuffer.clear()
                if (archive.readData(probeBuffer) > 0) {
                    // The entry is larger than its header claimed.
                    budget.release(cost)
                    isBudgetTransferred = true
                    dataBuffer.flip()
                    probeBuffer.flip()
                    extractLargeFile(
                        archive, entry, index, path, root, listOf(dataBuffer, probeBuffer)
                    )
                    return
                }
                tree.markNonDirectory(path, index)
                dispatch(
                    WriteTask(
                        path, target, data, dataBuffer.position(), entry.lastModifiedTimeOrNull,
                        isLocal, !tree.isInCreatedDirectory(path, root), cost
                    )
                )
                isBudgetTransferred = true
            } finally {
                if (!isBudgetTransferred) {
                    budget.release(cost)
                }
            }
        }

        @Throws(IOException::class)
        private fun extractLargeFile(
            archive: ReadArchive,
            entry: ReadArchive.Entry,
            index: Int,
            path: Path,
            root: Root,
            pendingBuffers: List<ByteBuffer>
        ) {
            var target = tree.getTarget(path, root)
            var mayExist = !tree.isInCreatedDirectory(path, root)
            var sink: Sink? = null
            while (sink == null) {
                throwIfInterrupted()
                try {
                    sink = openSink(path, target, isLocalTarget(target), mayExist)
                } catch (e: FileAlreadyExistsException) {
                    mayExist = true
                    target = resolveNonDirectoryConflict(path, target) ?: run {
                        tree.markSkipped(path, index)
                        return
                    }
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    e.printStackTrace()
                    mayExist = true
                    if (!resolveError(path, target, e)) {
                        tree.markSkipped(path, index)
                        return
                    }
                } catch (e: UnsupportedOperationException) {
                    if (!resolveError(path, target, IOException(e))) {
                        tree.markSkipped(path, index)
                        return
                    }
                }
            }
            var writeException: IOException? = null
            var successful = false
            try {
                for (buffer in pendingBuffers) {
                    val bytes = buffer.remaining().toLong()
                    sink.write(buffer)
                    callback.onProgress(path, bytes, 0)
                }
                val fd = sink.fd
                if (fd != null && pendingBuffers.isEmpty() && entry.size <= INTO_FD_MAX_SIZE) {
                    // The duplicate shares the file offset, and is only needed for its int value.
                    ParcelFileDescriptor.dup(fd).use { archive.readDataIntoFd(it.fd) }
                    callback.onProgress(path, entry.size, 0)
                } else {
                    val buffer = getInlineBuffer(sink.fd != null)
                    while (true) {
                        throwIfInterrupted()
                        buffer.clear()
                        val bytesRead = archive.readData(buffer)
                        if (bytesRead == 0) {
                            break
                        }
                        buffer.flip()
                        sink.write(buffer)
                        callback.onProgress(path, bytesRead.toLong(), 0)
                    }
                }
                sink.close()
                successful = true
            } catch (e: ArchiveException) {
                throw e
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                e.printStackTrace()
                writeException = e
            } finally {
                if (!successful) {
                    try {
                        sink.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    deleteQuietly(target)
                }
            }
            if (writeException != null) {
                if (resolveError(path, target, writeException)) {
                    // Part of the entry data has been consumed, so it must be read again.
                    throw ReopenException(index)
                }
                tree.markSkipped(path, index)
                return
            }
            setLastModifiedTime(target, entry.lastModifiedTimeOrNull, sink.fd != null, false)
            if (sink.fd != null) {
                scanPaths += target.toString()
            }
            tree.markNonDirectory(path, index)
            callback.onProgress(path, 0, 1)
        }

        @Throws(IOException::class)
        private fun openSink(
            source: Path,
            target: Path,
            isLocal: Boolean,
            mayExist: Boolean
        ): Sink =
            if (isLocal) {
                LocalSink(openLocalFile(target), target)
            } else {
                if (mayExist) {
                    checkForeignTargetAbsent(source, target)
                }
                ForeignSink(
                    target.newOutputStream(
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                    )
                )
            }

        private fun getInlineBuffer(isDirect: Boolean): ByteBuffer =
            if (isDirect) {
                directBuffer ?: ByteBuffer.allocateDirect(INLINE_BUFFER_SIZE)
                    .also { directBuffer = it }
            } else {
                heapBuffer ?: ByteBuffer.allocate(INLINE_BUFFER_SIZE).also { heapBuffer = it }
            }

        /**
         * @return the target to retry with, or `null` if the entry should be skipped
         */
        @Throws(IOException::class)
        private fun resolveNonDirectoryConflict(source: Path, target: Path): Path? =
            when (val decision = callback.onConflict(source, target)) {
                is ConflictDecision.Replace, is ConflictDecision.Merge ->
                    if (deleteForReplace(source, target)) target else null
                is ConflictDecision.Rename -> decision.target
                is ConflictDecision.Skip -> null
                is ConflictDecision.Cancel -> throw InterruptedIOException()
            }

        /**
         * @return whether the target was deleted, or `false` if the entry should be skipped
         */
        @Throws(IOException::class)
        private fun deleteForReplace(source: Path, target: Path): Boolean {
            // A pending write may be creating the same file, e.g. on a case-insensitive file
            // system.
            awaitIdle()
            while (true) {
                throwIfInterrupted()
                try {
                    target.deleteIfExists()
                    return true
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    e.printStackTrace()
                    if (!resolveError(source, target, e)) {
                        return false
                    }
                } catch (e: UnsupportedOperationException) {
                    if (!resolveError(source, target, IOException(e))) {
                        return false
                    }
                }
            }
        }

        /**
         * @return `true` to retry, or `false` to skip
         */
        @Throws(IOException::class)
        private fun resolveError(source: Path, target: Path, exception: IOException): Boolean =
            when (callback.onError(source, target, exception)) {
                ErrorDecision.RETRY -> true
                ErrorDecision.SKIP -> false
                ErrorDecision.CANCEL -> throw InterruptedIOException()
            }

        @Throws(IOException::class)
        private fun createMissingRoots() {
            for (root in roots) {
                if (tree.contains(root.source)) {
                    continue
                }
                if (root.source.nameCount == 0) {
                    // The archive root is always a directory, even if the archive is empty.
                    createDirectory(root.source, root, ExtractionTree.SYNTHETIC_INDEX, null, 0)
                } else {
                    val exception = NoSuchFileException(root.source.toString())
                    if (callback.onError(root.source, root.target, exception)
                        == ErrorDecision.CANCEL) {
                        throw InterruptedIOException()
                    }
                }
            }
        }

        private fun setDirectoryTimes() {
            // Deepest first, since setting times on a child modifies its parent.
            directoryTimes.sortByDescending { it.first.nameCount }
            for ((directory, lastModifiedTime) in directoryTimes) {
                setLastModifiedTime(directory, lastModifiedTime, false, true)
            }
        }

        private fun isLocalTarget(target: Path): Boolean {
            if (!target.isLinuxPath) {
                return false
            }
            val parent = target.parent ?: return false
            return localTargetCache.getOrPut(parent) {
                if (isRunningAsRoot) {
                    true
                } else {
                    when (Settings.ROOT_STRATEGY.valueCompat) {
                        RootStrategy.NEVER -> true
                        RootStrategy.AUTOMATIC ->
                            !(target as RootablePath).isRootRequired(false)
                        RootStrategy.ALWAYS -> false
                    }
                }
            }
        }

        // Writer pipeline

        @Throws(IOException::class)
        private fun acquireBudget(bytes: Long) {
            while (!budget.tryAcquire(bytes)) {
                val results = awaitResults()
                // Buffered bytes are only held by in-flight tasks or their pending results.
                check(results.isNotEmpty()) { "Budget exhausted with no write in flight" }
                handleResults(results)
            }
        }

        @Throws(IOException::class)
        private fun dispatch(task: WriteTask) {
            val pool = pool ?: createPool().also { pool = it }
            lock.withLock { ++inFlightTaskCount }
            try {
                pool.execute { runWriteTask(task) }
            } catch (e: RejectedExecutionException) {
                lock.withLock { --inFlightTaskCount }
                throw InterruptedIOException().apply { initCause(e) }
            }
        }

        private fun createPool(): ThreadPoolExecutor {
            val threadNumber = AtomicInteger()
            val threadFactory = ThreadFactory { runnable ->
                Thread(runnable, "ArchiveExtractor-writer-${threadNumber.incrementAndGet()}")
                    .apply { isDaemon = true }
            }
            return ThreadPoolExecutor(
                writerThreadCount, writerThreadCount, 0, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(), threadFactory
            )
        }

        // Runs on a writer thread.
        private fun runWriteTask(task: WriteTask) {
            var result: WriteResult? = null
            try {
                writeFile(task)
                setLastModifiedTime(task.target, task.lastModifiedTime, task.isLocal, false)
                result = WriteResult.Completed(task)
            } catch (e: FileAlreadyExistsException) {
                result = WriteResult.Conflict(task)
            } catch (e: IOException) {
                result = WriteResult.Failed(task, e)
            } catch (t: Throwable) {
                result = WriteResult.Failed(task, IOException(t))
            } finally {
                lock.withLock {
                    result?.let { results.addLast(it) }
                    --inFlightTaskCount
                    condition.signalAll()
                }
            }
        }

        @Throws(IOException::class)
        private fun writeFile(task: WriteTask) {
            val sink = openSink(task.source, task.target, task.isLocal, task.mayExist)
            var successful = false
            try {
                sink.write(task.data, task.length)
                sink.close()
                successful = true
            } finally {
                if (!successful) {
                    try {
                        sink.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    deleteQuietly(task.target)
                }
            }
        }

        private fun pollResults(): List<WriteResult> =
            lock.withLock {
                if (results.isEmpty()) {
                    emptyList()
                } else {
                    results.toList().also { results.clear() }
                }
            }

        /**
         * @return pending results, or an empty list if no write is in flight
         */
        @Throws(InterruptedIOException::class)
        private fun awaitResults(): List<WriteResult> =
            lock.withLock {
                while (results.isEmpty() && inFlightTaskCount > 0) {
                    awaitCondition()
                }
                results.toList().also { results.clear() }
            }

        @Throws(InterruptedIOException::class)
        private fun awaitIdle() {
            lock.withLock {
                while (inFlightTaskCount > 0) {
                    awaitCondition()
                }
            }
        }

        @Throws(InterruptedIOException::class)
        private fun awaitCondition() {
            try {
                condition.await()
            } catch (e: InterruptedException) {
                throw InterruptedIOException().apply { initCause(e) }
            }
        }

        @Throws(IOException::class)
        private fun awaitAllWrites() {
            while (true) {
                val results = awaitResults()
                if (results.isEmpty()) {
                    return
                }
                handleResults(results)
            }
        }

        @Throws(IOException::class)
        private fun handleResults(results: List<WriteResult>) {
            for (result in results) {
                val task = result.task
                when (result) {
                    is WriteResult.Completed -> {
                        budget.release(task.cost)
                        if (task.isLocal) {
                            scanPaths += task.target.toString()
                        }
                        callback.onProgress(task.source, task.length.toLong(), 1)
                    }
                    is WriteResult.Conflict -> {
                        task.mayExist = true
                        val target = resolveNonDirectoryConflict(task.source, task.target)
                        if (target != null) {
                            task.target = target
                            task.isLocal = isLocalTarget(target)
                            dispatch(task)
                        } else {
                            budget.release(task.cost)
                        }
                    }
                    is WriteResult.Failed -> {
                        result.exception.printStackTrace()
                        task.mayExist = true
                        if (resolveError(task.source, task.target, result.exception)) {
                            dispatch(task)
                        } else {
                            budget.release(task.cost)
                        }
                    }
                }
            }
        }

        private fun shutDownPool(successful: Boolean) {
            val pool = pool ?: return
            if (successful) {
                pool.shutdown()
            } else {
                // Interrupts writers; each of them deletes its own partially written file.
                pool.shutdownNow()
            }
            var interrupted = Thread.interrupted()
            for (i in 0 until SHUTDOWN_TIMEOUT_SECONDS) {
                try {
                    if (pool.awaitTermination(1, TimeUnit.SECONDS)) {
                        break
                    }
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            lock.withLock {
                for (result in results) {
                    if (result is WriteResult.Completed && result.task.isLocal) {
                        scanPaths += result.task.target.toString()
                    }
                }
                results.clear()
            }
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }

        private fun scanMedia() {
            if (isRunningAsRoot || scanPaths.isEmpty()) {
                return
            }
            try {
                MediaScannerConnection.scanFile(application, scanPaths.toTypedArray(), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private class ReopenException(val index: Int) : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private class WriteTask(
        val source: Path,
        var target: Path,
        val data: ByteArray,
        val length: Int,
        val lastModifiedTime: FileTime?,
        var isLocal: Boolean,
        // Whether to check for an existing foreign target before creating it.
        var mayExist: Boolean,
        val cost: Long
    )

    private sealed class WriteResult(val task: WriteTask) {
        class Completed(task: WriteTask) : WriteResult(task)

        class Conflict(task: WriteTask) : WriteResult(task)

        class Failed(task: WriteTask, val exception: IOException) : WriteResult(task)
    }

    private abstract class Sink : Closeable {
        open val fd: FileDescriptor?
            get() = null

        @Throws(IOException::class)
        abstract fun write(buffer: ByteBuffer)

        @Throws(IOException::class)
        abstract fun write(data: ByteArray, length: Int)
    }

    private class LocalSink(override val fd: FileDescriptor, private val target: Path) : Sink() {
        private var isClosed = false

        @Throws(IOException::class)
        override fun write(buffer: ByteBuffer) {
            try {
                while (buffer.hasRemaining()) {
                    Os.write(fd, buffer)
                }
            } catch (e: ErrnoException) {
                throw SyscallException(e).toFileSystemException(target.toString())
            }
        }

        @Throws(IOException::class)
        override fun write(data: ByteArray, length: Int) {
            try {
                var offset = 0
                while (offset < length) {
                    offset += Os.write(fd, data, offset, length - offset)
                }
            } catch (e: ErrnoException) {
                throw SyscallException(e).toFileSystemException(target.toString())
            }
        }

        @Throws(IOException::class)
        override fun close() {
            if (isClosed) {
                return
            }
            isClosed = true
            try {
                Syscall.close(fd)
            } catch (e: SyscallException) {
                throw e.toFileSystemException(target.toString())
            }
        }
    }

    private class ForeignSink(private val outputStream: OutputStream) : Sink() {
        private var isClosed = false

        @Throws(IOException::class)
        override fun write(buffer: ByteBuffer) {
            if (buffer.hasArray()) {
                outputStream.write(
                    buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()
                )
                buffer.position(buffer.limit())
            } else {
                val array = ByteArray(buffer.remaining())
                buffer.get(array)
                outputStream.write(array)
            }
        }

        @Throws(IOException::class)
        override fun write(data: ByteArray, length: Int) {
            outputStream.write(data, 0, length)
        }

        @Throws(IOException::class)
        override fun close() {
            if (isClosed) {
                return
            }
            isClosed = true
            outputStream.close()
        }
    }

    companion object {
        private const val ARCHIVE_BUFFER_SIZE = 1024 * 1024

        private const val INLINE_BUFFER_SIZE = 1024 * 1024

        // Above this, data is copied in chunks so that progress and cancellation stay responsive.
        private const val INTO_FD_MAX_SIZE = 64L * 1024 * 1024

        // Accounted for each buffered task in addition to its data, which also bounds the number
        // of queued tasks for empty files.
        private const val TASK_OVERHEAD_BYTES = 4096L

        private const val MAX_SMALL_FILE_SIZE = Int.MAX_VALUE - 8

        private const val SHUTDOWN_TIMEOUT_SECONDS = 60

        private val CREATE_NEW_FLAGS =
            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL

        private val CREATE_FILE_MODE = PosixFileMode.CREATE_FILE_DEFAULT.toInt()

        private val EPOCH = FileTime.fromMillis(0)

        private val ReadArchive.Entry.lastModifiedTimeOrNull: FileTime?
            get() = lastModifiedTime?.takeIf { it != EPOCH }

        @Throws(InterruptedIOException::class)
        private fun throwIfInterrupted() {
            if (Thread.interrupted()) {
                throw InterruptedIOException()
            }
        }

        @Throws(IOException::class)
        private fun openLocalFile(target: Path): FileDescriptor {
            val targetBytes = (target as ByteStringListPath<*>).toByteString()
            return try {
                Syscall.open(targetBytes, CREATE_NEW_FLAGS, CREATE_FILE_MODE)
            } catch (e: SyscallException) {
                e.maybeThrowInvalidFileNameException(targetBytes.toString())
                throw e.toFileSystemException(targetBytes.toString())
            }
        }

        /**
         * Foreign providers don't necessarily fail on creating an existing file or directory, so
         * check explicitly like ForeignCopyMove does, unless the parent directory was just created
         * by this extraction.
         */
        @Throws(IOException::class)
        private fun checkForeignTargetAbsent(source: Path, target: Path) {
            if (!target.isLinuxPath && target.exists(LinkOption.NOFOLLOW_LINKS)) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }

        private fun isDirectoryNoFollowLinks(path: Path): Boolean =
            try {
                path.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    .isDirectory
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }

        // Failures are only logged, like ForeignCopyMove does.
        private fun setLastModifiedTime(
            path: Path,
            lastModifiedTime: FileTime?,
            isLocal: Boolean,
            noFollowLinks: Boolean
        ) {
            if (lastModifiedTime == null) {
                return
            }
            if (isLocal) {
                val pathBytes = (path as ByteStringListPath<*>).toByteString()
                val instant = lastModifiedTime.toInstant()
                val times = arrayOf(
                    StructTimespec(0, Constants.UTIME_OMIT),
                    StructTimespec(instant.epochSecond, instant.nano.toLong())
                )
                try {
                    if (noFollowLinks) {
                        Syscall.lutimens(pathBytes, times)
                    } else {
                        Syscall.utimens(pathBytes, times)
                    }
                } catch (e: SyscallException) {
                    e.printStackTrace()
                }
                return
            }
            try {
                val linkOptions = if (noFollowLinks) {
                    arrayOf(LinkOption.NOFOLLOW_LINKS)
                } else {
                    emptyArray()
                }
                Files.getFileAttributeView(path, BasicFileAttributeView::class.java, *linkOptions)
                    ?.setTimes(lastModifiedTime, null, null)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: UnsupportedOperationException) {
                e.printStackTrace()
            }
        }

        private fun deleteQuietly(path: Path) {
            try {
                path.deleteIfExists()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: UnsupportedOperationException) {
                e.printStackTrace()
            }
        }
    }
}

/** Bounds the bytes buffered for writing; not thread-safe. */
internal class ByteBudget(private val maxBytes: Long) {
    var usedBytes = 0L
        private set

    /**
     * Always succeeds when nothing is held, so that a single request larger than the bound can't
     * block forever.
     */
    fun tryAcquire(bytes: Long): Boolean {
        require(bytes >= 0) { "bytes $bytes must not be negative" }
        if (usedBytes > 0 && usedBytes + bytes > maxBytes) {
            return false
        }
        usedBytes += bytes
        return true
    }

    fun release(bytes: Long) {
        require(bytes in 0..usedBytes) { "Releasing $bytes bytes with $usedBytes bytes held" }
        usedBytes -= bytes
    }
}

/**
 * Tracks which normalized archive paths have been handled during an extraction and where they were
 * extracted to, mirroring the tree built by ArchiveReader.readEntries.
 */
internal class ExtractionTree(private val roots: List<ArchiveExtractor.Root>) {
    private val nodes = HashMap<Path, Node>()

    private class Node(
        val index: Int,
        val type: Type,
        val target: Path?,
        val isCreated: Boolean
    )

    private enum class Type { DIRECTORY, NON_DIRECTORY, SKIPPED }

    sealed class Resolution {
        object NotSelected : Resolution()

        /** An ancestor was skipped or isn't a directory, so the entry isn't in the tree. */
        object Unreachable : Resolution()

        /** An earlier entry with the same path wins. */
        object Duplicate : Resolution()

        /** A directory entry for a directory that was created implicitly for earlier entries. */
        class LateDirectory(val target: Path, val isCreated: Boolean) : Resolution()

        /**
         * The entry should be extracted, after creating [missingAncestors] (from top to bottom)
         * implicitly.
         */
        class Pending(
            val root: ArchiveExtractor.Root,
            val missingAncestors: List<Path>
        ) : Resolution()
    }

    fun resolve(path: Path, index: Int, isDirectory: Boolean): Resolution {
        val root = roots.firstOrNull { path.startsWith(it.source) }
            ?: return Resolution.NotSelected
        val node = nodes[path]
        // The same index means the entry is being retried.
        if (node != null && node.index != index) {
            if (isDirectory && node.index == SYNTHETIC_INDEX && node.type == Type.DIRECTORY) {
                nodes[path] = Node(index, Type.DIRECTORY, node.target, node.isCreated)
                return Resolution.LateDirectory(node.target!!, node.isCreated)
            }
            return Resolution.Duplicate
        }
        var missingAncestors: MutableList<Path>? = null
        var ancestor = path
        while (ancestor != root.source) {
            ancestor = ancestor.parent ?: break
            val ancestorNode = nodes[ancestor]
            if (ancestorNode != null) {
                if (ancestorNode.type != Type.DIRECTORY) {
                    return Resolution.Unreachable
                }
                // Ancestors of an existing directory node exist as well.
                break
            }
            val ancestors = missingAncestors ?: mutableListOf<Path>().also { missingAncestors = it }
            ancestors.add(ancestor)
        }
        return Resolution.Pending(root, missingAncestors?.asReversed() ?: emptyList())
    }

    /** All ancestors of [path] under [root] must have been marked as directories. */
    fun getTarget(path: Path, root: ArchiveExtractor.Root): Path {
        if (path == root.source) {
            return root.target
        }
        val parentTarget = nodes[path.parent]?.target
            ?: throw IllegalStateException("Parent of $path has no target")
        return parentTarget.resolveForeign(path.fileName)
    }

    fun markDirectory(path: Path, index: Int, target: Path, isCreated: Boolean) {
        nodes[path] = Node(index, Type.DIRECTORY, target, isCreated)
    }

    fun markNonDirectory(path: Path, index: Int) {
        nodes[path] = Node(index, Type.NON_DIRECTORY, null, false)
    }

    fun markSkipped(path: Path, index: Int) {
        val node = nodes[path]
        if (node == null || node.index == index) {
            nodes[path] = Node(index, Type.SKIPPED, null, false)
        }
    }

    fun contains(path: Path): Boolean = path in nodes

    /** Whether the parent of [path] is a directory created by this extraction. */
    fun isInCreatedDirectory(path: Path, root: ArchiveExtractor.Root): Boolean =
        path != root.source && nodes[path.parent]?.isCreated == true

    companion object {
        const val SYNTHETIC_INDEX = -1
    }
}
