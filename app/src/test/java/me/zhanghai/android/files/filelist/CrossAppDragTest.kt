package me.zhanghai.android.files.filelist

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossAppDragTest {
    private fun rules(folders: Boolean, multiple: Boolean) = CrossAppDragRules(folders, multiple)

    @Test
    fun neitherFoldersNorMultipleAllowsOnlyASingleFile() {
        val r = rules(folders = false, multiple = false)
        assertTrue(r.allows(1, 0, false))
        assertFalse(r.allows(1, 1, false))
        assertFalse(r.allows(2, 0, false))
        assertFalse(r.allows(2, 1, false))
    }

    @Test
    fun foldersOnlyAllowsASingleFileOrASingleFolder() {
        val r = rules(folders = true, multiple = false)
        assertTrue(r.allows(1, 0, false))
        assertTrue(r.allows(1, 1, false))
        assertFalse(r.allows(2, 0, false))
        assertFalse(r.allows(2, 2, false))
    }

    @Test
    fun multipleOnlyAllowsOneOrMoreFilesButNoFolders() {
        val r = rules(folders = false, multiple = true)
        assertTrue(r.allows(1, 0, false))
        assertTrue(r.allows(5, 0, false))
        assertFalse(r.allows(1, 1, false))
        assertFalse(r.allows(5, 1, false))
    }

    @Test
    fun bothAllowAnySelection() {
        val r = rules(folders = true, multiple = true)
        assertTrue(r.allows(1, 1, false))
        assertTrue(r.allows(4, 2, false))
        assertTrue(r.allows(3, 3, false))
    }

    @Test
    fun emptySelectionsAndArchiveEntriesNeverDrag() {
        val r = rules(folders = true, multiple = true)
        assertFalse(r.allows(0, 0, false))
        assertFalse(r.allows(1, 0, true))
    }

    @Test
    fun storedHoldValuesAreRoundedToTenthsAndClamped() {
        assertEquals(2000, CrossAppDragHold.coerce(2000))
        assertEquals(1300, CrossAppDragHold.coerce(1340))
        assertEquals(500, CrossAppDragHold.coerce(0))
        assertEquals(500, CrossAppDragHold.coerce(-1))
        assertEquals(5000, CrossAppDragHold.coerce(60_000))
    }

    @Test
    fun theSliderSnapsToHalfSeconds() {
        assertEquals(500, CrossAppDragHold.snapToSlider(510f))
        assertEquals(1500, CrossAppDragHold.snapToSlider(1300f))
        assertEquals(2000, CrossAppDragHold.snapToSlider(1800f))
        assertEquals(5000, CrossAppDragHold.snapToSlider(5000f))
    }

    @Test
    fun typedSecondsAcceptTenthsWithEitherSeparator() {
        assertEquals(1300, CrossAppDragHold.parseSeconds("1.3"))
        assertEquals(1300, CrossAppDragHold.parseSeconds("1,3"))
        assertEquals(2000, CrossAppDragHold.parseSeconds("2"))
        assertEquals(500, CrossAppDragHold.parseSeconds("0.5"))
        assertEquals(5000, CrossAppDragHold.parseSeconds("5.0"))
        assertNull(CrossAppDragHold.parseSeconds("0.4"))
        assertNull(CrossAppDragHold.parseSeconds("5.1"))
        assertNull(CrossAppDragHold.parseSeconds("1.25"))
        assertNull(CrossAppDragHold.parseSeconds(""))
        assertNull(CrossAppDragHold.parseSeconds("abc"))
    }

    @Test
    fun theEntryFieldOnlyTakesASecondDigitAndATenth() {
        listOf("", "1", "1.", "1,", "1.3", "4,9").forEach {
            assertTrue(it, CrossAppDragHold.isAcceptableInput(it))
        }
        listOf("12", ".5", "1.25", "1..", "a", "-1").forEach {
            assertFalse(it, CrossAppDragHold.isAcceptableInput(it))
        }
    }

    @Test
    fun secondsFormatWithOneDecimalInTheUserLocale() {
        assertEquals("2.0", CrossAppDragHold.formatSeconds(2000, Locale.US))
        assertEquals("1,3", CrossAppDragHold.formatSeconds(1300, Locale.FRANCE))
    }
}
