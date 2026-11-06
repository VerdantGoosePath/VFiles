package me.zhanghai.android.files.filelist

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
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
        composeRule.onNodeWithText(
            context.getString(R.string.file_list_action_view_grid)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileViewType.GRID, mode.value) }
        composeRule.onNodeWithText(
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
        composeRule.onNodeWithText(context.getString(R.string.close)).performClick()
        composeRule.runOnIdle { assertEquals(false, expanded.value) }
    }
}
