package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileJobMetricsTest {
    @Test fun durationFormatsMatchSpecExamples() {
        assertEquals("43s", formatTaskDuration(43))
        assertEquals("55m 36s", formatTaskDuration(55 * 60 + 36))
        assertEquals("2h 15m", formatTaskDuration(2 * 3600 + 15 * 60))
        assertEquals("2h 15m", formatTaskDuration(2 * 3600 + 15 * 60 + 59))
    }

    @Test fun durationEdgesAndZeroTrailingUnits() {
        assertEquals("0s", formatTaskDuration(0))
        assertEquals("0s", formatTaskDuration(-5))
        assertEquals("59s", formatTaskDuration(59))
        assertEquals("1m", formatTaskDuration(60))
        assertEquals("59m 59s", formatTaskDuration(3599))
        assertEquals("1h", formatTaskDuration(3600))
        assertEquals("1d 1h", formatTaskDuration(90_000))
    }

    @Test fun durationUsesSuppliedPatterns() {
        val french = TaskDurationFormats(
            "%1\$d s", "%1\$d min", "%1\$d min %2\$d s", "%1\$d h", "%1\$d h %2\$d min",
            "%1\$d j", "%1\$d j %2\$d h"
        )
        assertEquals("55 min 36 s", formatTaskDuration(3336, french))
    }

    @Test fun etaRoundsUpAndHidesWhenUnknown() {
        assertEquals(43L, estimateRemainingSeconds(43_000, 1_000.0))
        assertEquals(1L, estimateRemainingSeconds(1, 1_000.0))
        assertEquals(0L, estimateRemainingSeconds(0, 1_000.0))
        assertNull(estimateRemainingSeconds(1_000, null))
        assertNull(estimateRemainingSeconds(1_000, 0.0))
        assertNull(estimateRemainingSeconds(1_000, Double.NaN))
        assertNull(estimateRemainingSeconds(-1, 10.0))
        // Absurd estimates (stalled transfer) are hidden rather than shown as months.
        assertNull(estimateRemainingSeconds(Long.MAX_VALUE / 2, 1.0))
    }

    @Test fun etaFormatsFromRealisticTransfer() {
        // 3.6 GB left at 12 MB/s is 300 s.
        val eta = estimateRemainingSeconds(3_600_000_000, 12_000_000.0)!!
        assertEquals("5m", formatTaskDuration(eta))
        val slow = estimateRemainingSeconds(3_336_000_000, 1_000_000.0)!!
        assertEquals("55m 36s", formatTaskDuration(slow))
    }

    @Test fun speedFormattingDelegatesToPlatformSizes() {
        val fake: (Long) -> String = { "$it B" }
        assertEquals("500 B/s", formatTransferSpeed(499.6, fake))
        assertEquals("0 B/s", formatTransferSpeed(-3.0, fake))
        assertEquals("0 B/s", formatTransferSpeed(Double.NaN, fake))
        assertEquals("12 B par s", formatTransferSpeed(12.0, fake, "%1\$s par s"))
    }

    @Test fun smootherConvergesToSteadyRate() {
        val smoother = FileJobRateSmoother()
        var bytes = 0L
        for (t in 0..60_000L step 500) {
            smoother.add(t, bytes)
            bytes += 500_000 // 1 MB/s
        }
        assertEquals(1_000_000.0, smoother.rate!!, 1.0)
    }

    @Test fun smootherDampsASpikeButFollowsSustainedChange() {
        val smoother = FileJobRateSmoother()
        var bytes = 0L
        var t = 0L
        repeat(20) { smoother.add(t, bytes); t += 500; bytes += 500_000 }
        // A single 10x burst moves the 3 s EWMA by well under half the gap.
        smoother.add(t, bytes + 5_000_000)
        val afterSpike = smoother.rate!!
        assertTrue(afterSpike < 1_000_000.0 + 0.5 * 9_000_000.0)
        assertTrue(afterSpike > 1_000_000.0)
    }

    @Test fun smootherIgnoresDuplicateAndBackwardSamples() {
        val smoother = FileJobRateSmoother()
        assertNull(smoother.add(0, 0))
        smoother.add(1_000, 1_000)
        assertEquals(1_000.0, smoother.rate!!, 0.001)
        smoother.add(1_000, 50_000)
        assertEquals(1_000.0, smoother.rate!!, 0.001)
        smoother.add(2_000, 10)
        assertEquals(1_000.0, smoother.rate!!, 0.001)
    }

    @Test fun stalledRateDecaysAfterGrace() {
        val smoother = FileJobRateSmoother()
        smoother.add(0, 0)
        smoother.add(1_000, 1_000)
        assertEquals(1_000.0, smoother.rateAt(2_000)!!, 0.001)
        assertTrue(smoother.rateAt(20_000)!! < 10.0)
    }

    @Test fun trackerKeepsRollingWindowAndAverage() {
        val tracker = FileJobSpeedTracker(windowMillis = 10_000)
        var bytes = 0L
        for (t in 0..30_000L step 500) {
            tracker.update(t, bytes, null)
            bytes += 1_000
        }
        val snapshot = tracker.snapshot(30_000)
        assertTrue(snapshot.samples.first().timeMillis >= 20_000)
        assertEquals(30_000L, snapshot.samples.last().timeMillis)
        assertEquals(2_000.0, snapshot.averageBytesPerSecond!!, 0.001)
        assertEquals(2_000.0, snapshot.bytesPerSecond!!, 1.0)
        assertNull(snapshot.filesPerSecond)
    }

    @Test fun trackerRateFallsBackToFiles() {
        val tracker = FileJobSpeedTracker()
        tracker.update(0, null, 0)
        tracker.update(1_000, null, 4)
        assertEquals(false, tracker.update(1_000, null, 9))
        val snapshot = tracker.snapshot(1_000)
        assertEquals(4.0, snapshot.filesPerSecond!!, 0.001)
        assertNull(snapshot.bytesPerSecond)
        assertTrue(snapshot.samples.isEmpty())
    }
}
