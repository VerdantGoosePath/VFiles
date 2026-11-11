/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import android.system.Os
import android.system.OsConstants
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor
import me.zhanghai.android.files.provider.archive.archiver.WriteArchive
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.newByteChannel
import me.zhanghai.android.libarchive.Archive
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Random

enum class FixtureFormat(val label: String, val extension: String, val format: Int, val filter: Int) {
    ZIP("zip", "zip", Archive.FORMAT_ZIP, Archive.FILTER_NONE),
    TAR("tar", "tar", Archive.FORMAT_TAR, Archive.FILTER_NONE),
    TAR_GZIP("tar.gz", "tar.gz", Archive.FORMAT_TAR, Archive.FILTER_GZIP),
    TAR_ZSTD("tar.zst", "tar.zst", Archive.FORMAT_TAR, Archive.FILTER_ZSTD),
    SEVEN_Z("7z", "7z", Archive.FORMAT_7ZIP, Archive.FILTER_NONE)
}

sealed class FixtureEntry {
    abstract val name: String

    data class Directory(override val name: String, val mtimeSeconds: Long) : FixtureEntry()

    data class RegularFile(
        override val name: String,
        val size: Long,
        val seed: Long,
        val mtimeSeconds: Long
    ) : FixtureEntry()

    data class SymbolicLink(
        override val name: String,
        val linkTarget: String,
        val mtimeSeconds: Long
    ) : FixtureEntry()
}

/** What a correctly extracted tree should contain, keyed by path relative to the target. */
sealed class Node {
    object Directory : Node() {
        override fun toString(): String = "Directory"
    }

    data class RegularFile(val size: Long, val sha256: String, val mtimeSeconds: Long?) : Node()

    data class SymbolicLink(val linkTarget: String) : Node()
}

object ArchiveFixtures {
    const val MIB = 1L shl 20

    /** Deterministic incompressible content, generated in chunks so 64 MiB files need no heap. */
    fun forEachContentChunk(seed: Long, size: Long, block: (ByteArray, Int) -> Unit) {
        val random = Random(seed)
        val buffer = ByteArray(256 * 1024)
        var remaining = size
        while (remaining > 0) {
            val length = minOf(remaining, buffer.size.toLong()).toInt()
            random.nextBytes(buffer)
            block(buffer, length)
            remaining -= length
        }
    }

    fun contentSha256(seed: Long, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        forEachContentChunk(seed, size) { buffer, length -> digest.update(buffer, 0, length) }
        return digest.digest().toHex()
    }

    @Throws(IOException::class)
    fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Throws(IOException::class)
    fun writeArchive(file: File, format: FixtureFormat, entries: List<FixtureEntry>) {
        file.parentFile!!.mkdirs()
        file.delete()
        val channel = file.toJava8Path().newByteChannel(
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        )
        channel.use {
            WriteArchive(channel, format.format, format.filter, null).use { archive ->
                for (entry in entries) {
                    val type: PosixFileType
                    val size: Long
                    val mtime: Long
                    var linkTarget: String? = null
                    val mode = when (entry) {
                        is FixtureEntry.Directory -> {
                            type = PosixFileType.DIRECTORY
                            size = 0
                            mtime = entry.mtimeSeconds
                            PosixFileMode.DIRECTORY_DEFAULT
                        }
                        is FixtureEntry.RegularFile -> {
                            type = PosixFileType.REGULAR_FILE
                            size = entry.size
                            mtime = entry.mtimeSeconds
                            PosixFileMode.FILE_DEFAULT
                        }
                        is FixtureEntry.SymbolicLink -> {
                            type = PosixFileType.SYMBOLIC_LINK
                            size = 0
                            mtime = entry.mtimeSeconds
                            linkTarget = entry.linkTarget
                            PosixFileMode.SYMBOLIC_LINK_DEFAULT
                        }
                    }
                    archive.Entry(
                        entry.name, FileTime.fromMillis(mtime * 1000), null, null, type, size,
                        null, null, mode, linkTarget
                    ).use { archive.writeEntry(it) }
                    if (entry is FixtureEntry.RegularFile) {
                        val outputStream = archive.newDataOutputStream()
                        forEachContentChunk(entry.seed, entry.size) { buffer, length ->
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
            }
        }
    }

    /**
     * Expected tree for [entries] extracted from archive directory [sourcePrefix] ("" for the
     * root). Directories implied by deeper entries are included; for duplicate names any one of
     * the candidates is accepted, see [assertTreeMatches].
     */
    fun expectedTree(
        entries: List<FixtureEntry>,
        sourcePrefix: String = "",
        checkMtimes: Boolean = true
    ): Map<String, List<Node>> {
        val prefix = if (sourcePrefix.isEmpty()) "" else sourcePrefix.trimEnd('/') + "/"
        val tree = linkedMapOf<String, MutableList<Node>>()
        for (entry in entries) {
            val name = entry.name.trimEnd('/')
            if (!name.startsWith(prefix) || isUnsafeName(name)) {
                continue
            }
            val relative = name.removePrefix(prefix)
            val segments = relative.split('/')
            for (i in 1 until segments.size) {
                tree.getOrPut(segments.subList(0, i).joinToString("/")) { mutableListOf() }
                    .apply { if (isEmpty()) add(Node.Directory) }
            }
            val node = when (entry) {
                is FixtureEntry.Directory -> Node.Directory
                is FixtureEntry.RegularFile -> Node.RegularFile(
                    entry.size, contentSha256(entry.seed, entry.size),
                    if (checkMtimes) entry.mtimeSeconds else null
                )
                is FixtureEntry.SymbolicLink -> Node.SymbolicLink(entry.linkTarget)
            }
            val candidates = tree.getOrPut(relative) { mutableListOf() }
            if (node == Node.Directory && candidates.isNotEmpty()) {
                continue
            }
            candidates.remove(Node.Directory)
            candidates += node
        }
        return tree
    }

    fun isUnsafeName(name: String): Boolean =
        name.startsWith("/") || name.split('/').any { it == ".." }

    /** Snapshot of an extracted tree on disk, relative to [root], without following links. */
    @Throws(IOException::class)
    fun snapshot(root: File, checkMtimes: Boolean = true): Map<String, Node> {
        val result = sortedMapOf<String, Node>()
        fun visit(file: File, relative: String) {
            val stat = Os.lstat(file.path)
            when {
                OsConstants.S_ISLNK(stat.st_mode) ->
                    result[relative] = Node.SymbolicLink(Os.readlink(file.path))
                OsConstants.S_ISDIR(stat.st_mode) -> {
                    if (relative.isNotEmpty()) {
                        result[relative] = Node.Directory
                    }
                    for (child in file.list()!!.sorted()) {
                        visit(File(file, child), if (relative.isEmpty()) child else "$relative/$child")
                    }
                }
                OsConstants.S_ISREG(stat.st_mode) -> result[relative] = Node.RegularFile(
                    stat.st_size, fileSha256(file),
                    if (checkMtimes) stat.st_mtime else null
                )
                else -> throw IOException("Unexpected file type: $file")
            }
        }
        visit(root, "")
        return result
    }

    fun assertTreeMatches(expected: Map<String, List<Node>>, actual: Map<String, Node>) {
        val missing = expected.keys - actual.keys
        val unexpected = actual.keys - expected.keys
        val mismatched = expected.keys.intersect(actual.keys).filter { actual[it] !in expected[it]!! }
            .map { "$it: expected one of ${expected[it]} but was ${actual[it]}" }
        if (missing.isNotEmpty() || unexpected.isNotEmpty() || mismatched.isNotEmpty()) {
            throw AssertionError(
                "Extracted tree mismatch\nmissing=$missing\nunexpected=$unexpected\n" +
                    mismatched.joinToString("\n")
            )
        }
    }

    fun File.toJava8Path(): Path = Paths.get(absolutePath)

    fun archiveRoot(archive: File): Path = archive.toJava8Path().createArchiveRootPath()

    /** Fresh archive file system so no state cached by an earlier case leaks into this one. */
    fun refreshedArchiveRoot(archive: File): Path =
        archiveRoot(archive).also { it.archiveRefresh() }

    fun deleteRecursively(file: File) {
        if (Os.lstat(file.path).let { OsConstants.S_ISDIR(it.st_mode) }) {
            file.list()?.forEach { deleteRecursively(File(file, it)) }
        }
        file.delete()
    }

    fun deleteIfExistsRecursively(file: File) {
        try {
            Os.lstat(file.path)
        } catch (e: android.system.ErrnoException) {
            return
        }
        deleteRecursively(file)
    }
}

/** Records callbacks and answers conflicts with a fixed decision for non-directories. */
open class RecordingCallback(
    private val fileConflictDecision: () -> ArchiveExtractor.ConflictDecision =
        { ArchiveExtractor.ConflictDecision.Cancel }
) : ArchiveExtractor.Callback {
    val conflicts = mutableListOf<Pair<Path, Path>>()
    val errors = mutableListOf<Pair<Path, IOException>>()
    var lastFiles = 0
    var lastBytes = 0L

    override fun onConflict(source: Path, target: Path): ArchiveExtractor.ConflictDecision {
        conflicts += source to target
        return if (File(target.toString()).isDirectory) {
            ArchiveExtractor.ConflictDecision.Merge
        } else {
            fileConflictDecision()
        }
    }

    override fun onError(
        source: Path,
        target: Path,
        exception: IOException
    ): ArchiveExtractor.ErrorDecision {
        errors += source to exception
        return ArchiveExtractor.ErrorDecision.CANCEL
    }

    override fun onProgress(currentSource: Path, bytes: Long, files: Int) {
        lastBytes = bytes
        lastFiles = files
    }
}
