@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.settings.ItemCountStyle
import me.zhanghai.android.files.settings.ViewTogglePlacement

/**
 * A scalar snapshot of mutable file/provider LiveData. File operation authority remains with
 * FileListViewModel and FileListFragment; composables only render it and dispatch intents.
 */
data class FileBrowserChromeState(
    val breadcrumbs: List<BreadcrumbUi> = emptyList(),
    /** Index of the folder on screen; the trail may continue past it after navigating up. */
    val currentIndex: Int = -1,
    val subtitle: String = "",
    /** Null while the folder is loading or failed; the header then shows [subtitle]. */
    val folderCount: Int? = null,
    val fileCount: Int? = null,
    val viewType: FileViewType = FileViewType.LIST,
    val sortOptions: FileSortOptions = FileSortOptions(
        FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true
    ),
    val pathSpecificSort: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val searchExpanded: Boolean = false,
    val searchQuery: String = "",
    val selectedCount: Int = 0,
    val selectedReadOnly: Boolean = false,
    val selectedArchivePaths: Boolean = false,
    val selectedArchiveFiles: Boolean = false,
    val currentReadOnly: Boolean = true,
    val canOpenInTerminal: Boolean = false,
    val canNavigateUp: Boolean = false,
    val picker: Boolean = false,
    val pickerCanSelectMultiple: Boolean = false,
    /** Title of an external picker session, e.g. "Choose a file for Docs". */
    val pickerTitle: String = "",
    val pickerMode: PickOptions.Mode? = null,
    val pickerFileName: String = "",
    val pasteCount: Int = 0,
    val pasteIsCopy: Boolean = false,
    /** Every pending source lives inside an archive, so pasting extracts. */
    val pasteIsExtract: Boolean = false,
    val viewTogglePlacement: ViewTogglePlacement = ViewTogglePlacement.BOTH,
    val itemCountStyle: ItemCountStyle = ItemCountStyle.BOTH,
    val showUpButton: Boolean = false,
    val longPressScrollToTop: Boolean = true,
    val swipeToOpenNavigation: Boolean = true
) {
    val title: String
        get() = (breadcrumbs.getOrNull(currentIndex) ?: breadcrumbs.lastOrNull())?.label.orEmpty()
}

enum class FileBrowserSelectionCommand {
    CLEAR, SELECT_ALL, PICK, CUT, COPY, DELETE, EXTRACT, ARCHIVE, SHARE, PASTE, CANCEL_PASTE
}

/** Folder-level commands formerly owned by the legacy toolbar overflow menu. */
enum class FileBrowserFolderAction {
    NEW_TASK, NAVIGATE_UP, NAVIGATE_TO, REFRESH, SELECT_ALL, SHOW_HIDDEN_FILES, SHARE, COPY_PATH,
    OPEN_IN_TERMINAL, ADD_BOOKMARK, CREATE_SHORTCUT
}
/**
 * [C34] Contextual selection actions in a vibrant floating toolbar near the thumb. Every command
 * is one tap away: archive and extract sit beside copy, cut, share and delete, with no overflow.
 */
@Composable
internal fun BrowserSelectionToolbar(
    state: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = state.commandContext().availableCommands()
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    ) {
        if (FileBrowserSelectionCommand.COPY in commands) {
            BrowserToolbarAction(
                if (state.selectedArchivePaths) R.drawable.extract_icon_control_normal_24dp
                else R.drawable.copy_icon_control_normal_24dp,
                stringResource(
                    if (state.selectedArchivePaths) R.string.file_list_select_action_extract
                    else R.string.copy
                )
            ) { onSelection(FileBrowserSelectionCommand.COPY) }
        }
        if (FileBrowserSelectionCommand.CUT in commands) {
            BrowserToolbarAction(R.drawable.cut_icon_control_normal_24dp, stringResource(R.string.cut)) {
                onSelection(FileBrowserSelectionCommand.CUT)
            }
        }
        if (FileBrowserSelectionCommand.SHARE in commands) {
            BrowserToolbarAction(R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share)) {
                onSelection(FileBrowserSelectionCommand.SHARE)
            }
        }
        if (FileBrowserSelectionCommand.EXTRACT in commands) {
            BrowserToolbarAction(
                R.drawable.extract_icon_control_normal_24dp,
                stringResource(R.string.file_list_select_action_extract)
            ) { onSelection(FileBrowserSelectionCommand.EXTRACT) }
        }
        if (FileBrowserSelectionCommand.ARCHIVE in commands) {
            BrowserToolbarAction(
                R.drawable.archive_icon_control_normal_24dp,
                stringResource(R.string.file_list_select_action_archive)
            ) { onSelection(FileBrowserSelectionCommand.ARCHIVE) }
        }
        if (FileBrowserSelectionCommand.DELETE in commands) {
            BrowserToolbarAction(R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete)) {
                onSelection(FileBrowserSelectionCommand.DELETE)
            }
        }
    }
}

@Composable
private fun BrowserToolbarAction(icon: Int, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/**
 * Pending paste: the destination is the folder on screen, so the toolbar's FAB is the paste
 * action ([C34]) and the label states what will land here.
 */
@Composable
internal fun BrowserPasteToolbar(
    state: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val canPaste = !state.currentReadOnly
    val pasteLabel = stringResource(
        if (state.pasteIsExtract) R.string.file_list_paste_action_extract_here
        else R.string.file_browser_paste_here
    )
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = { if (canPaste) onSelection(FileBrowserSelectionCommand.PASTE) },
                containerColor = if (canPaste) {
                    FloatingToolbarDefaults.vibrantFloatingToolbarColors().fabContainerColor
                } else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (canPaste) {
                    FloatingToolbarDefaults.vibrantFloatingToolbarColors().fabContentColor
                } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.semantics {
                    contentDescription = pasteLabel
                    if (!canPaste) disabled()
                }
            ) {
                Icon(painterResource(R.drawable.paste_icon_control_normal_24dp), contentDescription = null)
            }
        }
    ) {
        IconButton(
            onClick = { onSelection(FileBrowserSelectionCommand.CANCEL_PASTE) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Icon(
                painterResource(R.drawable.file_browser_icon_close),
                contentDescription = stringResource(android.R.string.cancel)
            )
        }
        Column(
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp, end = 12.dp)
        ) {
            Text(
                stringResource(
                    when {
                        state.pasteIsExtract -> R.string.file_list_paste_extract_title_format
                        state.pasteIsCopy -> R.string.file_list_paste_copy_title_format
                        else -> R.string.file_list_paste_move_title_format
                    },
                    state.pasteCount
                ),
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (canPaste) pasteLabel
                else stringResource(R.string.file_browser_badge_read_only),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
internal private fun folderActionTitle(action: FileBrowserFolderAction): Int = when (action) {
    FileBrowserFolderAction.NEW_TASK -> R.string.file_list_action_new_task
    FileBrowserFolderAction.NAVIGATE_UP -> R.string.file_list_action_navigate_up
    FileBrowserFolderAction.NAVIGATE_TO -> R.string.file_list_action_navigate_to
    FileBrowserFolderAction.REFRESH -> R.string.refresh
    FileBrowserFolderAction.SELECT_ALL -> R.string.select_all
    FileBrowserFolderAction.SHOW_HIDDEN_FILES -> R.string.file_list_action_show_hidden_files
    FileBrowserFolderAction.SHARE -> R.string.share
    FileBrowserFolderAction.COPY_PATH -> R.string.file_list_action_copy_path
    FileBrowserFolderAction.OPEN_IN_TERMINAL -> R.string.file_list_action_open_in_terminal
    FileBrowserFolderAction.ADD_BOOKMARK -> R.string.file_list_action_add_bookmark
    FileBrowserFolderAction.CREATE_SHORTCUT -> R.string.file_list_action_create_shortcut
}
