package me.zhanghai.android.files.filelist

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on device/emulator; CI compiles these with assembleDebugAndroidTest. */
@RunWith(AndroidJUnit4::class)
class FileBrowserComposeUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderChrome(
        mode: MutableState<FileViewType>,
        expanded: MutableState<Boolean>,
        query: MutableState<String>
    ) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                VFilesBrowserChrome(
                    state = FileBrowserChromeState(
                        viewType = mode.value, searchExpanded = expanded.value,
                        searchQuery = query.value
                    ),
                    onBreadcrumb = {},
                    onSearchExpanded = { expanded.value = it },
                    onSearchQuery = { query.value = it },
                    onSearchSubmit = {},
                    onViewType = { mode.value = it },
                    onSortBy = {},
                    onSortOrder = {},
                    onDirectoriesFirst = {},
                    onPathSpecificSort = {},
                    onShowHiddenFiles = {},
                    onSelection = {}
                )
            }
        }
    }

    @Test fun viewModeChoicesUpdateHoistedState() {
        val mode = mutableStateOf(FileViewType.LIST)
        renderChrome(mode, mutableStateOf(false), mutableStateOf(""))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_list_action_view_grid)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileViewType.GRID, mode.value) }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_list_action_view_list)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileViewType.LIST, mode.value) }
    }

    @Test fun searchMaintainsVisibleQueryAndCloses() {
        val expanded = mutableStateOf(false)
        val query = mutableStateOf("")
        renderChrome(mutableStateOf(FileViewType.LIST), expanded, query)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.search)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("backup")
        composeRule.runOnIdle {
            assertEquals(true, expanded.value)
            assertEquals("backup", query.value)
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        composeRule.runOnIdle { assertEquals(false, expanded.value) }
    }
    @Test fun sortSheetChangesHoistedSortState() {
        val sortBy = mutableStateOf(FileSortOptions.By.NAME)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                VFilesBrowserChrome(
                    state = FileBrowserChromeState(
                        sortOptions = FileSortOptions(
                            sortBy.value, FileSortOptions.Order.ASCENDING, true
                        )
                    ),
                    onBreadcrumb = {},
                    onSearchExpanded = {},
                    onSearchQuery = {},
                    onSearchSubmit = {},
                    onViewType = {},
                    onSortBy = { sortBy.value = it },
                    onSortOrder = {},
                    onDirectoriesFirst = {},
                    onPathSpecificSort = {},
                    onShowHiddenFiles = {},
                    onSelection = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_list_action_view_sort)
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.file_list_action_sort_by_size)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileSortOptions.By.SIZE, sortBy.value) }
    }

    @Test fun selectionDockDispatchesCopyAndClear() {
        val selected = mutableStateOf(FileBrowserSelectionCommand.SELECT_ALL)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                BrowserSelectionBar(
                    state = FileBrowserChromeState(selectedCount = 2),
                    onSelection = { selected.value = it }
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.copy)).performClick()
        composeRule.runOnIdle {
            assertEquals(FileBrowserSelectionCommand.COPY, selected.value)
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        composeRule.runOnIdle {
            assertEquals(FileBrowserSelectionCommand.CLEAR, selected.value)
        }
    }

}
