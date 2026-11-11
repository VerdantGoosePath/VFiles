/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.archivePasswords
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor.ConflictDecision
import me.zhanghai.android.files.provider.archive.archiver.ArchiveExtractor.ErrorDecision
import me.zhanghai.android.files.provider.common.UserActionRequiredException
import java.io.IOException

/**
 * Extracts [sources] in the same archive to [targets] in a single pass over the archive, with the
 * same dialogs and progress as copying them one by one.
 */
@Throws(IOException::class)
internal fun FileJob.extractArchive(
    sources: List<Path>,
    targets: List<Path>,
    transferInfo: TransferInfo,
    actionAllInfo: ActionAllInfo
) {
    val firstSource = sources.first()
    val extractor = ArchiveExtractor(firstSource.archiveFile, { firstSource.archivePasswords })
    val roots = sources.zip(targets) { source, target -> ArchiveExtractor.Root(source, target) }
    extractor.extract(roots, ExtractCallback(this, transferInfo, actionAllInfo))
}

private class ExtractCallback(
    private val job: FileJob,
    private val transferInfo: TransferInfo,
    private val actionAllInfo: ActionAllInfo
) : ArchiveExtractor.Callback {
    override fun onConflict(source: Path, target: Path): ConflictDecision {
        val sourceFile: FileItem
        val targetFile: FileItem
        try {
            sourceFile = source.loadFileItem()
            targetFile = target.loadFileItem()
        } catch (e: IOException) {
            e.printStackTrace()
            return onError(source, target, e).toConflictDecision(target)
        }
        val sourceIsDirectory = sourceFile.attributesNoFollowLinks.isDirectory
        val targetIsDirectory = targetFile.attributesNoFollowLinks.isDirectory
        if (!sourceIsDirectory && targetIsDirectory) {
            // TODO: Don't allow replace directory with file.
            val exception = FileAlreadyExistsException(source.toString(), target.toString(), null)
            return onError(source, target, exception).toConflictDecision(target)
        }
        val isMerge = sourceIsDirectory && targetIsDirectory
        if (isMerge && actionAllInfo.merge) {
            return ConflictDecision.Merge
        } else if (!isMerge && actionAllInfo.replace) {
            return ConflictDecision.Replace
        } else if ((isMerge && actionAllInfo.skipMerge)
            || (!isMerge && actionAllInfo.skipReplace)) {
            skipFile(source)
            return ConflictDecision.Skip
        }
        val result = job.showConflictDialog(sourceFile, targetFile, CopyMoveType.EXTRACT)
        return when (result.action) {
            FileJobConflictAction.MERGE_OR_REPLACE -> {
                if (result.isAll) {
                    if (isMerge) {
                        actionAllInfo.merge = true
                    } else {
                        actionAllInfo.replace = true
                    }
                }
                if (isMerge) ConflictDecision.Merge else ConflictDecision.Replace
            }
            FileJobConflictAction.RENAME ->
                ConflictDecision.Rename(target.resolveSibling(result.name!!))
            FileJobConflictAction.SKIP -> {
                if (result.isAll) {
                    if (isMerge) {
                        actionAllInfo.skipMerge = true
                    } else {
                        actionAllInfo.skipReplace = true
                    }
                }
                skipFile(source)
                ConflictDecision.Skip
            }
            FileJobConflictAction.CANCELED -> {
                skipFile(source)
                ConflictDecision.Skip
            }
            FileJobConflictAction.CANCEL -> ConflictDecision.Cancel
        }
    }

    // Retrying a conflict means trying the same target again.
    private fun ErrorDecision.toConflictDecision(target: Path): ConflictDecision =
        when (this) {
            ErrorDecision.RETRY -> ConflictDecision.Rename(target)
            ErrorDecision.SKIP -> ConflictDecision.Skip
            ErrorDecision.CANCEL -> ConflictDecision.Cancel
        }

    override fun onError(source: Path, target: Path, exception: IOException): ErrorDecision {
        if (actionAllInfo.skipCopyMoveError) {
            skipFile(source)
            return ErrorDecision.SKIP
        }
        if (exception is UserActionRequiredException) {
            if (job.showUserAction(exception)) {
                return ErrorDecision.RETRY
            }
        }
        val targetParent = target.parent ?: target
        val result = job.showErrorDialog(
            job.getString(R.string.file_job_extract_error_title_format, job.getFileName(source)),
            job.getString(
                R.string.file_job_extract_error_message_format, job.getFileName(targetParent),
                exception.toString()
            ),
            job.getReadOnlyFileStore(target, exception),
            true,
            job.getString(R.string.retry),
            job.getString(R.string.skip),
            job.getString(android.R.string.cancel)
        )
        return when (result.action) {
            FileJobErrorAction.POSITIVE -> ErrorDecision.RETRY
            FileJobErrorAction.NEGATIVE -> {
                if (result.isAll) {
                    actionAllInfo.skipCopyMoveError = true
                }
                skipFile(source)
                ErrorDecision.SKIP
            }
            FileJobErrorAction.CANCELED -> {
                skipFile(source)
                ErrorDecision.SKIP
            }
            FileJobErrorAction.NEUTRAL -> ErrorDecision.CANCEL
        }
    }

    override fun onProgress(currentSource: Path, bytes: Long, files: Int) {
        transferInfo.addToTransferredSize(bytes)
        repeat(files) { transferInfo.incrementTransferredFileCount() }
        job.postCopyMoveNotification(transferInfo, currentSource, CopyMoveType.EXTRACT)
    }

    private fun skipFile(source: Path) {
        transferInfo.skipFile(source)
        job.postCopyMoveNotification(transferInfo, source, CopyMoveType.EXTRACT)
    }
}
