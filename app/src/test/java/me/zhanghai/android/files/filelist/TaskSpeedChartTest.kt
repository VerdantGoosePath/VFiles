package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.filejob.FileJobRateSmoother
import me.zhanghai.android.files.filejob.FileJobSpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSpeedChartTest {
    @Test fun niceCeilPicksOneTwoTwoAndAHalfFive() {
        assertEquals(1_000f, niceCeil(1_000f))
        assertEquals(2_000f, niceCeil(1_001f))
        assertEquals(2_000f, niceCeil(2_000f))
        assertEquals(2_500f, niceCeil(2_100f))
        assertEquals(5_000f, niceCeil(2_600f))
        assertEquals(10_000f, niceCeil(5_100f))
        assertEquals(20_000_000f, niceCeil(13_640_000f))
        assertEquals(0.5f, niceCeil(0.31f))
        assertEquals(0f, niceCeil(0f))
        assertEquals(0f, niceCeil(Float.NaN))
    }

    @Test fun scaleGrowsAtOnceButShrinksWithHysteresis() {
        assertEquals(1_000f, nextScaleTarget(0f, 0f))
        assertEquals(20_000_000f, nextScaleTarget(0f, 12_400_000f))
        // Needs more room: grows immediately.
        assertEquals(25_000_000f, nextScaleTarget(20_000_000f, 19_000_000f))
        // Fits in a smaller scale, but not in half of this one: holds.
        assertEquals(20_000_000f, nextScaleTarget(20_000_000f, 9_500_000f))
        // Fits in half: shrinks to the nearest nice value.
        assertEquals(10_000_000f, nextScaleTarget(20_000_000f, 7_000_000f))
        assertEquals(1_000f, nextScaleTarget(20_000_000f, 0f))
    }

    @Test fun monotoneSlopesNeverOvershoot() {
        val values = floatArrayOf(0f, 0f, 10f, 10f, 2f, 9f, 9.5f, 0f, 0f)
        val slopes = monotoneSlopes(values)
        for (k in 0 until values.size - 1) {
            val low = minOf(values[k], values[k + 1])
            val high = maxOf(values[k], values[k + 1])
            for (step in 0..20) {
                val y = hermite(values[k], values[k + 1], slopes[k], slopes[k + 1], step / 20f)
                assertTrue("segment $k at $step: $y", y >= low - 1e-4f && y <= high + 1e-4f)
            }
        }
        // Flat runs and local extrema get horizontal tangents.
        assertEquals(0f, slopes[0])
        assertEquals(0f, slopes[2])
        assertEquals(0f, slopes[4])
    }

    @Test fun seriesResamplesSteadySpeedFromRest() {
        val samples = (1..20).map { FileJobSpeedSample(10_000L + it * 500, 1_000_000f) }
        val series = SpeedSeries.of(samples)!!
        assertEquals(0L, series.startMillis % StepMillis)
        // Ramps up from zero before the first sample.
        assertEquals(0f, series.valueAt(series.startMillis))
        assertEquals(1_000_000f, series.valueAt(13_000), 1f)
        assertEquals(1_000_000f, series.peak(9_000, 20_000), 1f)
        assertEquals(0f, series.values.last())
    }

    @Test fun seriesDecaysLikeTheSmootherAfterAStall() {
        val last = 20_000L
        val samples = listOf(FileJobSpeedSample(19_500, 1_000_000f), FileJobSpeedSample(last, 1_000_000f))
        val series = SpeedSeries.of(samples)!!
        assertEquals(1_000_000f, series.valueAt(last + 1_000), 1f)
        val later = last + FileJobRateSmoother.STALL_GRACE_MILLIS + 3_000
        // One time constant past the grace period: 1/e.
        assertEquals(367_879f, series.valueAt(later), 3_000f)
        assertEquals(0f, series.valueAt(last + 120_000))
    }

    @Test fun resumedTransferKeepsTheStallItShowed() {
        val stalled = SpeedSeries.of(listOf(FileJobSpeedSample(0, 0f), FileJobSpeedSample(500, 1_000_000f)))!!
        val resumed = SpeedSeries.of(
            listOf(
                FileJobSpeedSample(0, 0f), FileJobSpeedSample(500, 1_000_000f),
                FileJobSpeedSample(20_500, 50_000f)
            )
        )!!
        // Early in the gap the curve is the decay already shown; only its tail bends to the new
        // sample.
        val t = 5_000L
        assertEquals(stalled.valueAt(t), resumed.valueAt(t), stalled.valueAt(t) * 0.25f)
        assertEquals(50_000f, resumed.valueAt(20_500), 1_000f)
    }

    @Test fun chartWindowExpandsThenScrolls() {
        val young = SpeedSeries.of(listOf(FileJobSpeedSample(100_000, 5f)))!!
        val start = chartTime(young, 102_000, 60_000)
        assertEquals(101_000L, start.end)
        assertEquals(10_000L, start.span)
        val grown = chartTime(young, 130_000, 60_000)
        assertEquals(30_000L, grown.span)
        val full = chartTime(young, 300_000, 60_000)
        assertEquals(60_000L, full.span)
        // Never runs ahead of the newest sample's clock, nor shows the future.
        assertEquals(99_000L, chartTime(young, 0, 60_000).end)
    }

    @Test fun emptyHistoryHasNoSeries() {
        assertNull(SpeedSeries.of(emptyList()))
    }

    @Test fun scaleProgressIsLogarithmic() {
        assertEquals(1f, scaleProgress(10f, 10f, 10f))
        assertEquals(0f, scaleProgress(10f, 10f, 1_000f))
        assertEquals(0.5f, scaleProgress(100f, 10f, 1_000f), 1e-4f)
        assertEquals(1f, scaleProgress(1_000f, 10f, 1_000f))
    }
}
