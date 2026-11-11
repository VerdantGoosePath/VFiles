package me.zhanghai.android.files.filelist

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.Collator

/** Drag to select and the delayed hand-over between folders in the Compose browser. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h800dp-xxhdpi")
class FileBrowserInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun installProvider() {
        runCatching { FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider) }
    }

    private fun name(index: Int) = "Item %02d".format(index)

    private fun files(folder: String, count: Int): List<FileItem> = (0 until count).map { index ->
        val name = name(index)
        FileItem(
            Paths.get("$folder/$name"),
            Collator.getInstance().getCollationKey(name),
            PreviewFileAttributes(false, index * 1_000L), null, null, false,
            "text/plain".asMimeType()
        )
    }

    private fun centerOf(text: String, listTop: Float): Offset {
        val bounds = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        return Offset(bounds.center.x, bounds.center.y - listTop)
    }

    @Test
    fun longPressThenDragSelectsTheRangeAndRestoresRowsItLeaves() {
        val files = files("/drag", 30)
        var selected by mutableStateOf<Set<Path>>(setOf(files[1].path))
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                VFilesBrowserContent(
                    state = FileBrowserContentState(
                        pathKey = "/drag", files = files, loading = false,
                        selectedPaths = selected
                    ),
                    onRefresh = {},
                    onItemAction = { _, _ -> },
                    onRangeSelect = {},
                    onSetSelection = { changed, select ->
                        selected = if (select) selected + changed.map { it.path }
                            else selected - changed.map { it.path }.toSet()
                    },
                    contentPadding = PaddingValues()
                )
            }
        }
        composeRule.waitForIdle()
        val list = composeRule.onNode(hasScrollAction())
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val start = centerOf(name(3), listTop)
        val far = centerOf(name(6), listTop)
        val back = centerOf(name(4), listTop)
        list.performTouchInput {
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(start + Offset(0f, 1f))
            moveTo(far)
        }
        composeRule.waitForIdle()
        assertEquals((listOf(1) + (3..6)).map { files[it].path }.toSet(), selected)
        list.performTouchInput {
            moveTo(back)
            up()
        }
        composeRule.waitForIdle()
        // Rows 5 and 6 return to unselected; the pre-existing selection of row 1 is untouched.
        assertEquals(listOf(1, 3, 4).map { files[it].path }.toSet(), selected)
    }

    @Test
    fun aFastLoadSwapsFoldersWithoutShowingALoadingState() {
        val first = files("/a", 5)
        val second = files("/b", 5)
        var state by mutableStateOf(FileBrowserContentState(pathKey = "/a", files = first, loading = false))
        var displayed: DisplayedFiles? = null
        val history = mutableListOf<DisplayedFiles?>()
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                // A long hold makes "the load finished first" deterministic under test.
                displayed = rememberDisplayedFiles(state, loadingDelayMillis = 60_000)
                androidx.compose.runtime.SideEffect { history += displayed }
            }
        }
        composeRule.waitForIdle()
        assertEquals("/a", displayed?.pathKey)

        state = FileBrowserContentState(pathKey = "/b", loading = true)
        composeRule.waitForIdle()
        // Still showing the previous folder instead of a blank or spinner frame.
        assertEquals("/a", displayed?.pathKey)
        state = FileBrowserContentState(pathKey = "/b", files = second, loading = false)
        // waitForIdle (not waitUntil) also waits for the off-main sort to hand back its result.
        composeRule.waitForIdle()
        assert(history.none { it?.pathKey == "/b" && it.loading }) { "showed a loading frame" }
        assertEquals("/b", displayed?.pathKey)
        assertEquals(5, displayed?.files?.size)
        assertEquals(false, displayed?.loading)
    }

    @Test
    fun aSlowLoadShowsTheLoadingStateOnlyAfterTheDelay() {
        var state by mutableStateOf(FileBrowserContentState(pathKey = "/a", files = files("/a", 3), loading = false))
        var displayed: DisplayedFiles? = null
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                displayed = rememberDisplayedFiles(state)
            }
        }
        composeRule.waitForIdle()
        state = FileBrowserContentState(pathKey = "/slow", loading = true)
        composeRule.waitForIdle()
        assertEquals("/a", displayed?.pathKey)
        composeRule.waitUntil(timeoutMillis = 5_000) { displayed?.pathKey == "/slow" }
        assertEquals(true, displayed?.loading)
        assertEquals(0, displayed?.files?.size)
    }

    @Test
    fun titleFollowsTheCurrentCrumbNotTheEndOfTheTrail() {
        val crumbs = listOf("Internal", "Download", "Music").mapIndexed { index, label ->
            me.zhanghai.android.files.compose.components.BreadcrumbUi(index.toString(), label)
        }
        assertEquals("Download", FileBrowserChromeState(breadcrumbs = crumbs, currentIndex = 1).title)
        assertEquals("Music", FileBrowserChromeState(breadcrumbs = crumbs).title)
        assertNull(FileBrowserChromeState().title.takeIf { it.isNotEmpty() })
    }

    private class HoldHarness(val files: List<FileItem>, initial: Set<Int>) {
        var selected by mutableStateOf(initial.map { files[it].path }.toSet())
        var drags = 0
        fun indices() = files.indices.filter { files[it].path in selected }.toSet()
    }

    private fun setHoldContent(
        harness: HoldHarness,
        dragToSelect: Boolean = true,
        ready: Boolean = true
    ) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                VFilesBrowserContent(
                    state = FileBrowserContentState(
                        pathKey = "/hold", files = harness.files, loading = false,
                        selectedPaths = harness.selected, dragToSelect = dragToSelect,
                        crossAppDragHoldMillis = 2_000, crossAppDragReady = ready
                    ),
                    onRefresh = {},
                    onItemAction = { file, action ->
                        if (action == FileBrowserItemAction.SELECT) {
                            // Path is Iterable, so plus/minus need the element as a set.
                            harness.selected = if (file.path in harness.selected) {
                                harness.selected - setOf(file.path)
                            } else harness.selected + setOf(file.path)
                        }
                    },
                    onRangeSelect = {},
                    onSetSelection = { changed, select ->
                        harness.selected = if (select) harness.selected + changed.map { it.path }
                            else harness.selected - changed.map { it.path }.toSet()
                    },
                    contentPadding = PaddingValues(),
                    onStartCrossAppDrag = { _, _ -> harness.drags++; true }
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Presses [index], waits [millis] with a sub-slop wiggle, then lifts. */
    private fun hold(index: Int, millis: Long, release: Boolean = true) {
        val list = composeRule.onNode(hasScrollAction())
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val start = centerOf(name(index), listTop)
        list.performTouchInput {
            down(start)
            advanceEventTime(millis)
            moveTo(start + Offset(0f, 1f))
            if (release) up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun holdingASelectedItemStartsACrossAppDragAndKeepsTheSelection() {
        val harness = HoldHarness(files("/hold", 20), setOf(2, 3))
        setHoldContent(harness)
        hold(3, 2_100)
        assertEquals(1, harness.drags)
        assertEquals(setOf(2, 3), harness.indices())
    }

    @Test
    fun releasingASelectedItemBeforeTheHoldDeselectsItWithoutDragging() {
        val harness = HoldHarness(files("/hold", 20), setOf(2, 3))
        setHoldContent(harness)
        hold(3, 1_000)
        assertEquals(0, harness.drags)
        assertEquals(setOf(2), harness.indices())
    }

    @Test
    fun aShortTapOnASelectedItemStillToggles() {
        val harness = HoldHarness(files("/hold", 20), setOf(2, 3))
        setHoldContent(harness)
        hold(3, 50)
        assertEquals(0, harness.drags)
        // A tap reaches the row's click, which toggles while a selection exists.
        assertEquals(setOf(2), harness.indices())
    }

    @Test
    fun holdingAnUnselectedItemSelectsItAndNeverDrags() {
        val harness = HoldHarness(files("/hold", 20), setOf(2))
        setHoldContent(harness)
        hold(5, 2_500)
        assertEquals(0, harness.drags)
        assertEquals(setOf(2, 5), harness.indices())
    }

    @Test
    fun movingAfterTheLongPressOnASelectedItemDragsToDeselect() {
        val files = files("/hold", 20)
        val harness = HoldHarness(files, setOf(3, 4, 5, 6))
        setHoldContent(harness)
        val list = composeRule.onNode(hasScrollAction())
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val start = centerOf(name(3), listTop)
        val end = centerOf(name(5), listTop)
        list.performTouchInput {
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            moveTo(start + Offset(0f, 1f))
            moveTo(end)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, harness.drags)
        assertEquals(setOf(6), harness.indices())
    }

    @Test
    fun aSelectionOutsideTheRulesKeepsTheNormalLongPress() {
        val harness = HoldHarness(files("/hold", 20), setOf(2, 3))
        setHoldContent(harness, ready = false)
        hold(3, 2_500)
        assertEquals(0, harness.drags)
        assertEquals(setOf(2), harness.indices())
    }

    @Test
    fun withoutDragToSelectTheListStillOwnsLongPressAndHold() {
        val harness = HoldHarness(files("/hold", 20), emptySet())
        setHoldContent(harness, dragToSelect = false)
        hold(4, 800)
        assertEquals(setOf(4), harness.indices())
        hold(4, 2_100)
        assertEquals(1, harness.drags)
        assertEquals(setOf(4), harness.indices())
    }
}
