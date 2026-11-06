package me.zhanghai.android.files.provider.archive.archiver

import java8.nio.file.Path
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.archive.ArchivePasswordRequiredException
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.newInputStream
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Stages any VFiles Path provider (SAF, SMB, root, cloud, etc.) to private cache.
 * Rust handles all format parsing and decoding; no libarchive code remains.
 */
object ArchiveReader {
    private fun archiveFormat(path: Path): String {
        val name = path.fileName.toString().lowercase()
        return when {
            name.endsWith(".zip") || name.endsWith(".apk") || name.endsWith(".jar") ||
                name.endsWith(".cbz") -> "zip"
            name.endsWith(".7z") -> "7z"
            name.endsWith(".rar") || name.endsWith(".cbr") -> "rar"
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> "tar.xz"
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> "tar.gz"
            name.endsWith(".tar") -> "tar"
            else -> throw IOException("Unsupported archive format: $name")
        }
    }

    private fun stage(path: Path): File {
        val suffix = "." + archiveFormat(path)
        val temporary = File.createTempFile("vfiles-input-", suffix, application.cacheDir)
        try {
            path.newInputStream().use { source ->
                temporary.outputStream().use { target -> source.copyTo(target) }
            }
        } catch (e: Throwable) {
            temporary.delete()
            throw e
        }
        return temporary
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && !name.startsWith("\\") &&
            !name.contains('\\') && !name.contains('\u0000') &&
            !name.split('/').contains("..") &&
            !(name.length > 1 && name[1] == ':')

    private fun metadata(json: JSONObject): ReadArchive.Entry {
        val directory = json.optBoolean("directory")
        val symlink = json.optBoolean("symlink")
        val type = when {
            directory -> PosixFileType.DIRECTORY
            symlink -> PosixFileType.SYMBOLIC_LINK
            else -> PosixFileType.REGULAR_FILE
        }
        val mode = when {
            directory -> PosixFileMode.DIRECTORY_DEFAULT
            symlink -> PosixFileMode.SYMBOLIC_LINK_DEFAULT
            else -> PosixFileMode.FILE_DEFAULT
        }
        val modifiedTime = if (json.isNull("modified_ms")) null else
            FileTime.fromMillis(json.optLong("modified_ms"))
        val linkTarget = if (json.isNull("link_target")) null else
            json.optString("link_target").takeIf { it.isNotEmpty() }
        return ReadArchive.Entry(json.getString("name"), json.optBoolean("encrypted"),
            modifiedTime, null, null, type, json.optLong("size").coerceAtLeast(0),
            null, null, mode, linkTarget)
    }

    private fun parse(file: Path, passwords: List<String>): List<ReadArchive.Entry> {
        val source = stage(file)
        try {
            var last: IOException? = null
            for (password in (passwords.asReversed() + "").distinct()) {
                try {
                    val array = RustArchiveBridge.list(source, password, archiveFormat(file))
                        .getJSONArray("entries")
                    return buildList {
                        for (index in 0 until array.length()) {
                            val value = array.getJSONObject(index)
                            if (isSafeName(value.getString("name"))) add(metadata(value))
                        }
                    }
                } catch (e: IOException) {
                    last = e
                }
            }
            val failure = last ?: IOException("Cannot read archive")
            if (failure.message?.contains("password", ignoreCase = true) == true ||
                failure.message?.contains("encrypted", ignoreCase = true) == true) {
                throw ArchivePasswordRequiredException(file, failure.message)
            }
            throw failure
        } finally {
            source.delete()
        }
    }

    @Throws(IOException::class)
    fun readEntries(file: Path, passwords: List<String>, rootPath: Path)
        : Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>> {
        val entries = mutableMapOf<Path, ReadArchive.Entry>()
        for (entry in parse(file, passwords)) {
            val normalizedName = entry.name.trimEnd('/')
            if (!isSafeName(normalizedName)) continue
            val path = rootPath.resolve(normalizedName).normalize()
            if (!path.startsWith(rootPath) || path == rootPath) continue
            entries.putIfAbsent(path, entry)
        }
        entries.putIfAbsent(rootPath, directoryEntry(""))
        val tree = mutableMapOf<Path, MutableList<Path>>(rootPath to mutableListOf())
        for (initial in entries.keys.toList()) {
            var path = initial
            while (path != rootPath) {
                val parent = path.parent ?: break
                if (!parent.startsWith(rootPath)) break
                if (entries[path]?.isDirectory == true) tree.getOrPut(path) { mutableListOf() }
                tree.getOrPut(parent) { mutableListOf() }.apply {
                    if (!contains(path)) add(path)
                }
                if (entries.containsKey(parent)) break
                entries[parent] = directoryEntry(parent.toString())
                path = parent
            }
        }
        return entries to tree
    }

    private fun directoryEntry(name: String) = ReadArchive.Entry(
        name, false, null, null, null, PosixFileType.DIRECTORY, 0L, null, null,
        PosixFileMode.DIRECTORY_DEFAULT, null
    )

    @Throws(IOException::class)
    fun newInputStream(file: Path, passwords: List<String>, entry: ReadArchive.Entry): InputStream? {
        if (!isSafeName(entry.name) || entry.isDirectory || entry.isSymbolicLink) return null
        val source = stage(file)
        val target = File.createTempFile("vfiles-entry-", ".tmp", application.cacheDir)
        var success = false
        try {
            var last: IOException? = null
            for (password in (passwords.asReversed() + "").distinct()) {
                try {
                    RustArchiveBridge.extract(source, entry.name, target, password, archiveFormat(file))
                    success = true
                    break
                } catch (e: IOException) {
                    last = e
                }
            }
            if (!success) {
                val failure = last ?: IOException("Cannot decompress archive entry")
                if (failure.message?.contains("password", ignoreCase = true) == true ||
                    failure.message?.contains("encrypted", ignoreCase = true) == true) {
                    throw ArchivePasswordRequiredException(file, failure.message)
                }
                throw failure
            }
            return object : FilterInputStream(target.inputStream()) {
                override fun close() {
                    try { super.close() } finally { target.delete() }
                }
            }
        } finally {
            source.delete()
            if (!success) target.delete()
        }
    }
}
