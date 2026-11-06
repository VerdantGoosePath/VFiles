package me.zhanghai.android.files.provider.archive.archiver

import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser

/**
 * Format-neutral metadata returned from the Rust archive reader.
 * Kept as a standalone DTO so the virtual filesystem retains its public contract.
 */
object ReadArchive {
    class Entry(
        val name: String,
        val isEncrypted: Boolean,
        val lastModifiedTime: FileTime?,
        val lastAccessTime: FileTime?,
        val creationTime: FileTime?,
        val type: PosixFileType,
        val size: Long,
        val owner: PosixUser?,
        val group: PosixGroup?,
        val mode: Set<PosixFileModeBit>,
        val symbolicLinkTarget: String?
    ) {
        val isDirectory get() = type == PosixFileType.DIRECTORY
        val isSymbolicLink get() = type == PosixFileType.SYMBOLIC_LINK
    }
}
