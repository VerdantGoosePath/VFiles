package me.zhanghai.android.files.filejob

import kotlin.math.ceil
import kotlin.math.exp

/**
 * Exponentially weighted moving average of how fast a counter grows, per second. The weight of
 * each sample depends on the time since the previous one, so irregular update spacing (the
 * notification throttle, pauses for dialogs) does not skew the average.
 */
class FileJobRateSmoother(private val timeConstantMillis: Long = DEFAULT_TIME_CONSTANT_MILLIS) {
    private var hasSample = false
    private var lastTimeMillis = 0L
    private var lastValue = 0L

    var rate: Double? = null
        private set

    val lastSampleTimeMillis: Long?
        get() = if (hasSample) lastTimeMillis else null

    fun add(timeMillis: Long, value: Long): Double? {
        if (!hasSample) {
            hasSample = true
            lastTimeMillis = timeMillis
            lastValue = value
            return rate
        }
        val elapsedMillis = timeMillis - lastTimeMillis
        if (elapsedMillis <= 0) {
            return rate
        }
        // Skipped files shrink totals, never progress, but a retry can restart a counter; rebase
        // instead of recording a negative speed.
        if (value >= lastValue) {
            val instantRate = (value - lastValue) * 1000.0 / elapsedMillis
            val alpha = 1 - exp(-elapsedMillis.toDouble() / timeConstantMillis)
            rate = rate?.let { it + alpha * (instantRate - it) } ?: instantRate
        }
        lastTimeMillis = timeMillis
        lastValue = value
        return rate
    }

    /**
     * The rate as seen at [nowMillis]: once updates stop arriving for longer than [graceMillis]
     * (a stalled network share, a conflict dialog), the speed decays toward zero instead of
     * freezing at its last value.
     */
    fun rateAt(nowMillis: Long, graceMillis: Long = STALL_GRACE_MILLIS): Double? {
        val rate = rate ?: return null
        val silentMillis = nowMillis - lastTimeMillis - graceMillis
        return if (silentMillis <= 0) rate else rate * exp(-silentMillis.toDouble() / timeConstantMillis)
    }

    companion object {
        const val DEFAULT_TIME_CONSTANT_MILLIS = 3_000L
        const val STALL_GRACE_MILLIS = 1_500L
    }
}

data class FileJobSpeedSample(val timeMillis: Long, val bytesPerSecond: Float)

data class FileJobSpeedSnapshot(
    /** Smoothed bytes per second; null until two byte samples exist. */
    val bytesPerSecond: Double? = null,
    /** Smoothed files per second, the ETA fallback for jobs that only count files. */
    val filesPerSecond: Double? = null,
    val averageBytesPerSecond: Double? = null,
    val samples: List<FileJobSpeedSample> = emptyList()
)

/**
 * Per-job speed history; fed with each progress snapshot, at most every notification interval.
 * Samples are kept [HISTORY_SLACK_MILLIS] longer than [windowMillis], so a chart of the window
 * scrolling between updates always has data at its left edge.
 */
class FileJobSpeedTracker(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    timeConstantMillis: Long = FileJobRateSmoother.DEFAULT_TIME_CONSTANT_MILLIS
) {
    private val bytes = FileJobRateSmoother(timeConstantMillis)
    private val files = FileJobRateSmoother(timeConstantMillis)
    private val samples = ArrayDeque<FileJobSpeedSample>()
    private var firstByteTimeMillis = 0L
    private var firstBytes = -1L
    private var lastBytes = 0L
    private var lastByteTimeMillis = 0L

    fun update(timeMillis: Long, processedBytes: Long?, processedFiles: Int?): Boolean {
        val previous = bytes.lastSampleTimeMillis ?: files.lastSampleTimeMillis
        if (previous != null && timeMillis <= previous) {
            return false
        }
        if (processedFiles != null) {
            files.add(timeMillis, processedFiles.toLong())
        }
        if (processedBytes != null) {
            if (firstBytes < 0) {
                firstBytes = processedBytes
                firstByteTimeMillis = timeMillis
            }
            lastBytes = processedBytes
            lastByteTimeMillis = timeMillis
            bytes.add(timeMillis, processedBytes)?.let {
                samples.addLast(FileJobSpeedSample(timeMillis, it.toFloat()))
            }
            val oldest = timeMillis - windowMillis - HISTORY_SLACK_MILLIS
            while (samples.isNotEmpty() && samples.first().timeMillis < oldest) {
                samples.removeFirst()
            }
        }
        return true
    }

    fun snapshot(nowMillis: Long): FileJobSpeedSnapshot {
        val elapsed = lastByteTimeMillis - firstByteTimeMillis
        val average = if (firstBytes >= 0 && elapsed > 0) {
            (lastBytes - firstBytes) * 1000.0 / elapsed
        } else {
            null
        }
        return FileJobSpeedSnapshot(
            bytes.rateAt(nowMillis), files.rateAt(nowMillis), average, samples.toList()
        )
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 60_000L
        const val HISTORY_SLACK_MILLIS = 5_000L
    }
}

/** Seconds until [remaining] units are done at [ratePerSecond], or null when unknowable. */
fun estimateRemainingSeconds(remaining: Long, ratePerSecond: Double?): Long? {
    if (ratePerSecond == null || ratePerSecond.isNaN() || ratePerSecond <= MIN_RATE ||
        remaining < 0) {
        return null
    }
    val seconds = ceil(remaining / ratePerSecond)
    return if (seconds > MAX_ESTIMATE_SECONDS) null else seconds.toLong()
}

// Below this the estimate is noise; above the cap it is not worth showing.
private const val MIN_RATE = 1e-3
private const val MAX_ESTIMATE_SECONDS = 99.0 * 24 * 60 * 60

/** Format patterns for [formatTaskDuration]; the defaults match the English resources. */
data class TaskDurationFormats(
    val seconds: String = "%1\$ds",
    val minutes: String = "%1\$dm",
    val minutesSeconds: String = "%1\$dm %2\$ds",
    val hours: String = "%1\$dh",
    val hoursMinutes: String = "%1\$dh %2\$dm",
    val days: String = "%1\$dd",
    val daysHours: String = "%1\$dd %2\$dh"
)

/** Compact two-unit duration: "43s", "55m 36s", "2h 15m", "3d 4h"; zero trailing units drop. */
fun formatTaskDuration(totalSeconds: Long, formats: TaskDurationFormats = TaskDurationFormats()): String {
    val s = totalSeconds.coerceAtLeast(0)
    return when {
        s < 60 -> formats.seconds.format(s)
        s < 3_600 -> pair(formats.minutes, formats.minutesSeconds, s / 60, s % 60)
        s < 86_400 -> pair(formats.hours, formats.hoursMinutes, s / 3_600, s % 3_600 / 60)
        else -> pair(formats.days, formats.daysHours, s / 86_400, s % 86_400 / 3_600)
    }
}

private fun pair(major: String, both: String, first: Long, second: Long): String =
    if (second == 0L) major.format(first) else both.format(first, second)

/**
 * "12.4 MB/s" from a byte rate; [formatSize] is the platform size formatter so units follow the
 * locale. Negative or NaN rates (clock skew) render as zero rather than garbage.
 */
fun formatTransferSpeed(
    bytesPerSecond: Double,
    formatSize: (Long) -> String,
    pattern: String = "%1\$s/s"
): String {
    val bytes = if (bytesPerSecond.isNaN() || bytesPerSecond < 0) 0L else Math.round(bytesPerSecond)
    return pattern.format(formatSize(bytes))
}
