package me.zhanghai.android.files.filelist

import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.navigation.NavigationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on device/emulator; CI compiles these with assembleDebugAndroidTest. */
@RunWith(AndroidJUnit4::class)
class FileBrowserComposeUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun viewModeToggleUpdatesHoistedState() {
        val mode = mutableStateOf(FileViewType.LIST)
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                BrowserLocationHeader(
                    state = FileBrowserChromeState(viewType = mode.value),
                    onBreadcrumb = {},
                    onViewType = { mode.value = it },
                    onOpenSort = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_list_action_view_grid)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileViewType.GRID, mode.value) }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_list_action_view_list)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileViewType.LIST, mode.value) }
    }

    @Test fun sortSheetChangesHoistedSortState() {
        val sortBy = mutableStateOf(FileSortOptions.By.NAME)
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                BrowserSortSheet(
                    state = FileBrowserChromeState(
                        sortOptions = FileSortOptions(
                            sortBy.value, FileSortOptions.Order.ASCENDING, true
                        )
                    ),
                    onDismiss = {},
                    onSortBy = { sortBy.value = it },
                    onSortOrder = {},
                    onDirectoriesFirst = {},
                    onPathSpecificSort = {},
                    onShowHiddenFiles = {}
                )
            }
        }
        composeRule.onNodeWithText(
            context.getString(R.string.file_list_action_sort_by_size)
        ).performClick()
        composeRule.runOnIdle { assertEquals(FileSortOptions.By.SIZE, sortBy.value) }
    }

    @Test fun selectionToolbarDispatchesCommands() {
        val selected = mutableStateOf<FileBrowserSelectionCommand?>(null)
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                BrowserSelectionToolbar(
                    state = FileBrowserChromeState(selectedCount = 2),
                    onSelection = { selected.value = it }
                )
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.copy)).performClick()
        composeRule.runOnIdle { assertEquals(FileBrowserSelectionCommand.COPY, selected.value) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
        composeRule.runOnIdle { assertEquals(FileBrowserSelectionCommand.DELETE, selected.value) }
    }

    @Test fun searchAppBarReportsQueryAndCloses() {
        val actions = RecordingActions()
        renderScreen(actions, mutableStateOf(FileBrowserChromeState(breadcrumbs = TRAIL)))
        composeRule.onNodeWithText(
            context.getString(R.string.file_browser_search_in_format, "Download")
        ).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("backup")
        composeRule.waitUntil(5_000) { actions.submitted == "backup" }
        composeRule.runOnIdle {
            assertEquals(true, actions.searchActive)
            assertEquals("backup", actions.query)
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_browser_close_search)
        ).performClick()
        composeRule.waitUntil(5_000) { actions.searchActive == false }
    }

    @Test fun selectionModeShowsContextualBarAndClears() {
        val actions = RecordingActions()
        renderScreen(
            actions, mutableStateOf(FileBrowserChromeState(breadcrumbs = TRAIL, selectedCount = 3))
        )
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.file_browser_selected_count, 3, 3)
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.file_browser_clear_selection)
        ).performClick()
        composeRule.runOnIdle {
            assertTrue(FileBrowserSelectionCommand.CLEAR in actions.commands)
        }
    }

    private fun renderScreen(
        actions: FileBrowserScreenActions,
        chrome: MutableState<FileBrowserChromeState>
    ) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                FileBrowserScreen(
                    chrome = chrome.value,
                    content = FileBrowserContentState(loading = false),
                    jobs = emptyList(),
                    navigation = FileBrowserNavigationState(),
                    navigationListener = NoNavigation,
                    createMenuExpanded = false,
                    actions = actions
                )
            }
        }
    }

    private companion object {
        val TRAIL = listOf(BreadcrumbUi("0", "Internal storage"), BreadcrumbUi("1", "Download"))
    }
}

internal object NoNavigation : NavigationItem.Listener {
    override val currentPath: Path get() = Paths.get("/")
    override fun navigateTo(path: Path) {}
    override fun navigateToRoot(path: Path) {}
    override fun launchIntent(intent: Intent) {}
    override fun showToast(textRes: Int) {}
    override fun closeNavigationDrawer() {}
}

internal open class RecordingActions : FileBrowserScreenActions {
    var searchActive: Boolean? = null
    var query: String? = null
    var submitted: String? = null
    val commands = mutableListOf<FileBrowserSelectionCommand>()

    override fun onBreadcrumb(id: String) {}
    override fun onViewType(viewType: FileViewType) {}
    override fun onSortBy(by: FileSortOptions.By) {}
    override fun onSortOrder(order: FileSortOptions.Order) {}
    override fun onDirectoriesFirst(directoriesFirst: Boolean) {}
    override fun onPathSpecificSort(pathSpecific: Boolean) {}
    override fun onShowHiddenFiles(show: Boolean) {}
    override fun onSearchActiveChange(active: Boolean) { searchActive = active }
    override fun onSearchQueryChange(query: String) { this.query = query }
    override fun onSearchSubmit(query: String) { submitted = query }
    override fun onRefresh() {}
    override fun onItemAction(file: FileItem, action: FileBrowserItemAction) {}
    override fun onRangeSelect(files: List<FileItem>) {}
    override fun onSelection(command: FileBrowserSelectionCommand) { commands += command }
    override fun onFolderAction(action: FileBrowserFolderAction) {}
    override fun onCreateMenuExpandedChange(expanded: Boolean) {}
    override fun onCreateFile() {}
    override fun onCreateDirectory() {}
    override fun onCancelJob(id: Int) {}
    override fun onNavigationOpenChange(open: Boolean) {}
    override fun onTogglePersistentNavigation() {}
}
