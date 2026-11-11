package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskEtaTest {
    @Test
    fun roundsToCoarserStepsAsEstimatesGrow() {
        assertEquals(1L, roundTaskEtaSeconds(0))
        assertEquals(7L, roundTaskEtaSeconds(7))
        assertEquals(50L, roundTaskEtaSeconds(47))
        assertEquals(300L, roundTaskEtaSeconds(291))
        assertEquals(1_500L, roundTaskEtaSeconds(1_500))
        assertEquals(1_560L, roundTaskEtaSeconds(1_501))
        assertEquals(8_100L, roundTaskEtaSeconds(8_000))
    }

    @Test
    fun countsDownWithTheClockAndIgnoresSmallRises() {
        val smoother = TaskEtaSmoother()
        assertEquals(100L, smoother.update(0, 100))
        assertEquals(99L, smoother.update(1_000, 101))
        assertEquals(98L, smoother.update(2_000, 100))
    }

    @Test
    fun driftsTowardLowerEstimates() {
        val smoother = TaskEtaSmoother()
        smoother.update(0, 100)
        // Projected 99, pulled 30% of the way to 89.
        assertEquals(96L, smoother.update(1_000, 89))
    }

    @Test
    fun followsClearRisesAndSnapsOnLargeChanges() {
        val smoother = TaskEtaSmoother()
        smoother.update(0, 100)
        // 30 s above the projected 99 is past the tolerance, so it rises part of the way.
        assertEquals(108L, smoother.update(1_000, 129))
        assertEquals(400L, smoother.update(2_000, 400))
    }

    @Test
    fun repeatedQueriesForTheSameFrameDoNotPullTwice() {
        val smoother = TaskEtaSmoother()
        smoother.update(0, 100)
        val first = smoother.update(1_000, 80)
        assertEquals(first, smoother.update(1_000, 80))
    }

    @Test
    fun unknownEstimateResets() {
        val smoother = TaskEtaSmoother()
        smoother.update(0, 100)
        assertNull(smoother.update(1_000, null))
        assertEquals(40L, smoother.update(2_000, 40))
    }
}
