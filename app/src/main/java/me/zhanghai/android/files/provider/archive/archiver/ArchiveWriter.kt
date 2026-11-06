package me.zhanghai.android.files.provider.archive.archiver

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.readAttributes
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Bridges Android VFiles paths to Rust's native filesystem API.
 * Sources are staged in an isolated directory so remote providers continue to work.
 */
class ArchiveWriter(
    private val channel: SeekableByteChannel,
    format: Int,
    @Suppress("UNUSED_PARAMETER") filter: Int,
    private val password: String?,
    private val optionsOverride: String? = null
) : Closeable {
    private val formatName = ArchiveFormats.name(format)
    private val workDirectory = File.createTempFile("vfiles-archive-", "", application.cacheDir)
        .apply { delete(); if (!mkdir()) throw IOException("Cannot create staging directory") }
    private val archiveOutput = File(application.cacheDir, workDirectory.name + ".$formatName")
    private var successful = false

    @Throws(IOException::class)
    fun write(file: Path, entryName: Path, intervalMillis: Long, listener: ((Long) -> Unit)?) {
        val relative = entryName.toString().replace('\\', '/')
        if (relative.isBlank() || relative.startsWith("/") || relative.split('/').any { it == ".." } ||
            relative.indexOf(':') >= 0) throw IOException("Unsafe archive member path: $relative")
        val staged = File(workDirectory, relative)
        val attributes = file.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isSymbolicLink) {
            throw IOException("Symbolic links cannot be archived by the staging bridge")
        }
        if (attributes.isDirectory) {
            if (!staged.isDirectory && !staged.mkdirs()) throw IOException("Cannot stage directory: $relative")
        } else {
            val parent = staged.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot stage directory: $parent")
            file.newInputStream(LinkOption.NOFOLLOW_LINKS).use { input ->
                staged.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        listener?.invoke(count.toLong())
                    }
                }
            }
        }
    }

    override fun close() {
        try {
            RustArchiveBridge.create(workDirectory, archiveOutput, formatName, password.orEmpty(), optionsOverride)
            archiveOutput.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, count)
                    while (byteBuffer.hasRemaining()) channel.write(byteBuffer)
                }
            }
            successful = true
        } finally {
            archiveOutput.delete()
            workDirectory.deleteRecursively()
        }
    }
}
