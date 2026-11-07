package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rendering a known-only average would lie about unfinished indeterminate transfers. */
class JobProgressAggregateTest {
    @Test fun emptyAndUnknownJobsDoNotClaimPercentage() {
        assertNull(aggregateFileJobFraction(emptyList()))
        assertNull(aggregateFileJobFraction(listOf(null)))
        assertNull(aggregateFileJobFraction(listOf(0.75f, null, 0.5f)))
    }

    @Test fun allKnownJobsProduceClampedProgress() {
        assertEquals(0.5f, aggregateFileJobFraction(listOf(0.25f, 0.75f))!!, 0.0001f)
        assertEquals(0.5f, aggregateFileJobFraction(listOf(-1f, 2f))!!, 0.0001f)
    }
}
