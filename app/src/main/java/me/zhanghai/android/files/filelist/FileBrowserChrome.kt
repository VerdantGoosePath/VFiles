package me.zhanghai.android.files.filelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi

/**
 * A scalar snapshot of mutable file/provider LiveData. File operation authority remains with
 * FileListViewModel and FileListFragment. This composable is only the browser's navigation chrome.
 */
data class FileBrowserChromeState(
    val breadcrumbs: List<BreadcrumbUi> = emptyList(),
    val subtitle: String = "",
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
    val picker: Boolean = false,
    val pickerCanSelectMultiple: Boolean = false,
    val pasteCount: Int = 0,
    val pasteIsCopy: Boolean = false
)

enum class FileBrowserSelectionCommand {
    CLEAR, SELECT_ALL, PICK, CUT, COPY, DELETE, EXTRACT, ARCHIVE, SHARE, PASTE, CANCEL_PASTE
}

/**
 * [P01]/[V00] of the Android 17 guide: files are the hero, not six equally strong controls.
 * Navigation is a quiet single-line trail; search receives the largest touch target; view and
 * sort are distinct secondary controls. Sort options live in a dismissible native bottom sheet.
 */
@Composable
fun VFilesBrowserChrome(
    state: FileBrowserChromeState,
    onBreadcrumb: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onViewType: (FileViewType) -> Unit,
    onSortBy: (FileSortOptions.By) -> Unit,
    onSortOrder: (FileSortOptions.Order) -> Unit,
    onDirectoriesFirst: (Boolean) -> Unit,
    onPathSpecificSort: (Boolean) -> Unit,
    onShowHiddenFiles: (Boolean) -> Unit,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    BackHandler(state.searchExpanded && !sortSheetOpen) { onSearchExpanded(false) }
    LaunchedEffect(state.searchExpanded) {
        if (state.searchExpanded) searchFocus.requestFocus()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The toolbar already reports the file count. Do not echo its subtitle again.
            if (state.breadcrumbs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { index, segment ->
                        if (index > 0) {
                            Icon(
                                painterResource(R.drawable.arrow_end_icon_white_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index == state.breadcrumbs.lastIndex) {
                            Text(
                                segment.label,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        } else {
                            TextButton(onClick = { onBreadcrumb(segment.id) }) {
                                Text(segment.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.searchExpanded) {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQuery,
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.file_browser_icon_search), null)
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQuery("") }) {
                                    Icon(
                                        painterResource(R.drawable.file_browser_icon_close),
                                        contentDescription = stringResource(R.string.file_browser_clear_search)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            onSearchSubmit()
                            keyboard?.hide()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                    IconButton(onClick = {
                        keyboard?.hide()
                        onSearchExpanded(false)
                    }) {
                        Icon(
                            painterResource(R.drawable.file_browser_icon_close),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        onClick = { onSearchExpanded(true) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.file_browser_icon_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            onViewType(if (state.viewType == FileViewType.LIST)
                                FileViewType.GRID else FileViewType.LIST)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painterResource(if (state.viewType == FileViewType.LIST)
                                R.drawable.file_browser_icon_grid
                                else R.drawable.file_browser_icon_list),
                            contentDescription = stringResource(
                                if (state.viewType == FileViewType.LIST)
                                    R.string.file_list_action_view_grid
                                else R.string.file_list_action_view_list
                            )
                        )
                    }
                    IconButton(onClick = { sortSheetOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painterResource(R.drawable.file_browser_icon_sort),
                            contentDescription = stringResource(R.string.file_list_action_view_sort),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            // Contextual file operations are docked over content, not stacked beneath search.
        }
    }
    if (sortSheetOpen) {
        BrowserSortSheet(
            state = state,
            onDismiss = { sortSheetOpen = false },
            onSortBy = onSortBy,
            onSortOrder = onSortOrder,
            onDirectoriesFirst = onDirectoriesFirst,
            onPathSpecificSort = onPathSpecificSort,
            onShowHiddenFiles = onShowHiddenFiles
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserSortSheet(
    state: FileBrowserChromeState,
    onDismiss: () -> Unit,
    onSortBy: (FileSortOptions.By) -> Unit,
    onSortOrder: (FileSortOptions.Order) -> Unit,
    onDirectoriesFirst: (Boolean) -> Unit,
    onPathSpecificSort: (Boolean) -> Unit,
    onShowHiddenFiles: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.file_list_action_view_sort),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Native segmented shapes work as a coherent *group* only when their
            // seams use SegmentedGap instead of the section's large spacing.
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                FileSortOptions.By.entries.forEachIndexed { index, by ->
                val label = when (by) {
                    FileSortOptions.By.NAME -> R.string.file_list_action_sort_by_name
                    FileSortOptions.By.TYPE -> R.string.file_list_action_sort_by_type
                    FileSortOptions.By.SIZE -> R.string.file_list_action_sort_by_size
                    FileSortOptions.By.LAST_MODIFIED -> R.string.file_list_action_sort_by_last_modified
                }
                val selected = state.sortOptions.by == by
                SegmentedListItem(
                    onClick = { onSortBy(by) },
                    shapes = ListItemDefaults.segmentedShapes(index, FileSortOptions.By.entries.size),
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    content = {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                )
                }
            }
            Text(
                stringResource(R.string.file_browser_sort_options),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            BrowserSwitchRow(
                stringResource(R.string.file_list_action_sort_order_ascending),
                state.sortOptions.order == FileSortOptions.Order.ASCENDING,
                index = 0, count = 4
            ) { checked -> onSortOrder(
                if (checked) FileSortOptions.Order.ASCENDING else FileSortOptions.Order.DESCENDING
            ) }
            BrowserSwitchRow(
                stringResource(R.string.file_list_action_sort_directories_first),
                state.sortOptions.isDirectoriesFirst,
                index = 1, count = 4, onChange = onDirectoriesFirst
            )
            BrowserSwitchRow(
                stringResource(R.string.file_list_action_view_sort_path_specific),
                state.pathSpecificSort,
                index = 2, count = 4, onChange = onPathSpecificSort
            )
            BrowserSwitchRow(
                stringResource(R.string.file_list_action_show_hidden_files),
                state.showHiddenFiles,
                index = 3, count = 4, onChange = onShowHiddenFiles
            )
            }
        }
    }
}

@Composable
private fun BrowserSwitchRow(
    title: String,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit
) {
    // The segmented row is the only input owner; the switch is a visual state indicator.
    // Native item shapes create shared groups without four competing outlined cards.
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        content = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    )
}

@Composable
internal fun BrowserSelectionBar(
    state: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    var moreOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.file_list_select_title_format, state.selectedCount) +
                        " " + stringResource(R.string.file_browser_selected),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                TextButton(onClick = { onSelection(FileBrowserSelectionCommand.SELECT_ALL) }) {
                    Text(stringResource(R.string.select_all))
                }
                IconButton(onClick = { onSelection(FileBrowserSelectionCommand.CLEAR) }) {
                    Icon(painterResource(R.drawable.file_browser_icon_close),
                        contentDescription = stringResource(R.string.close))
                }
            }
            if (!state.picker) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onSelection(FileBrowserSelectionCommand.COPY) }
                    ) {
                        Text(stringResource(if (state.selectedArchivePaths)
                            R.string.file_list_select_action_extract else R.string.copy))
                    }
                    if (!state.selectedReadOnly) {
                        OutlinedButton(
                            onClick = { onSelection(FileBrowserSelectionCommand.CUT) }
                        ) { Text(stringResource(R.string.cut)) }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(
                                painterResource(R.drawable.file_browser_icon_more),
                                contentDescription = stringResource(R.string.file_browser_item_actions)
                            )
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            @Composable
                            fun action(title: Int, command: FileBrowserSelectionCommand) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(title)) },
                                    onClick = { moreOpen = false; onSelection(command) }
                                )
                            }
                            if (!state.selectedReadOnly) {
                                action(R.string.delete, FileBrowserSelectionCommand.DELETE)
                            }
                            if (state.selectedArchiveFiles) {
                                action(R.string.file_list_select_action_extract,
                                    FileBrowserSelectionCommand.EXTRACT)
                            }
                            if (!state.currentReadOnly) {
                                action(R.string.file_list_select_action_archive,
                                    FileBrowserSelectionCommand.ARCHIVE)
                            }
                            action(R.string.share, FileBrowserSelectionCommand.SHARE)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserPasteBar(
    state: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(
                    if (state.pasteIsCopy) R.string.file_list_paste_copy_title_format
                    else R.string.file_list_paste_move_title_format,
                    state.pasteCount
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            FilledTonalButton(
                enabled = !state.currentReadOnly,
                onClick = { onSelection(FileBrowserSelectionCommand.PASTE) }
            ) { Text(stringResource(R.string.paste)) }
            IconButton(onClick = { onSelection(FileBrowserSelectionCommand.CANCEL_PASTE) }) {
                Icon(painterResource(R.drawable.file_browser_icon_close),
                    contentDescription = stringResource(R.string.close))
            }
        }
    }
}
