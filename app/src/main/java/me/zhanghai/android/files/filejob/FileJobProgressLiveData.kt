/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData

/** What a file job is doing, so the UI can pick a verb and an icon without parsing its title. */
enum class FileJobKind { COPY, MOVE, EXTRACT, DELETE, ARCHIVE, WRITE, OTHER }

/** A snapshot of one in-flight file job, mirrored from its progress notification. */
data class FileJobProgress(
    val id: Int,
    val title: String,
    val detail: String?,
    // null means indeterminate (e.g. while scanning).
    val fraction: Float?,
    val kind: FileJobKind = FileJobKind.OTHER,
    /** Byte totals; null when the job only counts files (delete, permissions) or is scanning. */
    val totalBytes: Long? = null,
    val processedBytes: Long? = null,
    val totalFiles: Int? = null,
    val processedFiles: Int? = null,
    /** Name of the file being processed right now, unlike [detail] which may be "3 / 12". */
    val currentFileName: String? = null,
    /** [SystemClock.elapsedRealtime] of the first update of this job. */
    val startedAtMillis: Long? = null,
    /** [SystemClock.elapsedRealtime] of this snapshot, the time base for speed sampling. */
    val updatedAtMillis: Long? = null
)

/**
 * Live list of the file jobs currently running, so the UI can show in-app progress alongside the
 * notification. Updated (off the main thread) from [postNotification] and cleared when a job ends.
 */
object FileJobProgressLiveData : MutableLiveData<List<FileJobProgress>>(emptyList()) {
    private val progresses = LinkedHashMap<Int, FileJobProgress>()

    fun update(
        id: Int,
        title: CharSequence,
        detail: CharSequence?,
        max: Int,
        progress: Int,
        indeterminate: Boolean,
        kind: FileJobKind = FileJobKind.OTHER,
        totalBytes: Long? = null,
        processedBytes: Long? = null,
        totalFiles: Int? = null,
        processedFiles: Int? = null,
        currentFileName: String? = null
    ) {
        val fraction = if (indeterminate || max <= 0) {
            null
        } else {
            (progress.toFloat() / max).coerceIn(0f, 1f)
        }
        val now = SystemClock.elapsedRealtime()
        synchronized(progresses) {
            val startedAt = progresses[id]?.startedAtMillis ?: now
            progresses[id] = FileJobProgress(
                id, title.toString(), detail?.toString(), fraction, kind, totalBytes,
                processedBytes, totalFiles, processedFiles, currentFileName, startedAt, now
            )
            postValue(progresses.values.toList())
        }
    }

    fun remove(id: Int) {
        synchronized(progresses) {
            if (progresses.remove(id) != null) {
                postValue(progresses.values.toList())
            }
        }
    }
}
