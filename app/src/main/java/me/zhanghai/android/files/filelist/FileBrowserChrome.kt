package me.zhanghai.android.files.filelist

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.components.VFilesBreadcrumbs

/** Snapshot: FileItemSet and PasteState mutate in-place, so observe their scalar projections. */
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

/** Composable file-browser controls. All file/provider IO and commands remain in the Fragment. */
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
    var sortMenuOpen by remember { mutableStateOf(false) }
    BackHandler(state.searchExpanded) { onSearchExpanded(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(state.searchExpanded) {
        if (state.searchExpanded) searchFocus.requestFocus()
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.searchExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQuery,
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        label = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            onSearchSubmit()
                            keyboard?.hide()
                        }),
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                TextButton(onClick = { onSearchQuery("") }) { Text("×") }
                            }
                        }
                    )
                    TextButton(onClick = {
                        keyboard?.hide()
                        onSearchExpanded(false)
                    }) { Text(stringResource(R.string.close)) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { onSearchExpanded(true) }) {
                        Text(stringResource(R.string.search))
                    }
                    FilterChip(
                        selected = state.viewType == FileViewType.LIST,
                        onClick = { onViewType(FileViewType.LIST) },
                        label = { Text(stringResource(R.string.file_list_action_view_list)) }
                    )
                    FilterChip(
                        selected = state.viewType == FileViewType.GRID,
                        onClick = { onViewType(FileViewType.GRID) },
                        label = { Text(stringResource(R.string.file_list_action_view_grid)) }
                    )
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text(stringResource(R.string.file_list_action_view_sort))
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        FileSortOptions.By.entries.forEach { by ->
                            val title = when (by) {
                                FileSortOptions.By.NAME -> R.string.file_list_action_sort_by_name
                                FileSortOptions.By.TYPE -> R.string.file_list_action_sort_by_type
                                FileSortOptions.By.SIZE -> R.string.file_list_action_sort_by_size
                                FileSortOptions.By.LAST_MODIFIED ->
                                    R.string.file_list_action_sort_by_last_modified
                            }
                            val checked = state.sortOptions.by == by
                            DropdownMenuItem(
                                text = { Text(stringResource(title)) },
                                onClick = { onSortBy(by); sortMenuOpen = false },
                                trailingIcon = { if (checked) Text("✓") },
                                modifier = Modifier.semantics {
                                    selected = checked
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_list_action_sort_order_ascending)) },
                            onClick = {
                                onSortOrder(
                                    if (state.sortOptions.order == FileSortOptions.Order.ASCENDING)
                                        FileSortOptions.Order.DESCENDING
                                    else FileSortOptions.Order.ASCENDING
                                )
                            },
                            trailingIcon = {
                                if (state.sortOptions.order == FileSortOptions.Order.ASCENDING) Text("✓")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_list_action_sort_directories_first)) },
                            onClick = { onDirectoriesFirst(!state.sortOptions.isDirectoriesFirst) },
                            trailingIcon = { if (state.sortOptions.isDirectoriesFirst) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_list_action_view_sort_path_specific)) },
                            onClick = { onPathSpecificSort(!state.pathSpecificSort) },
                            trailingIcon = { if (state.pathSpecificSort) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_list_action_show_hidden_files)) },
                            onClick = { onShowHiddenFiles(!state.showHiddenFiles) },
                            trailingIcon = { if (state.showHiddenFiles) Text("✓") }
                        )
                    }
                }
            }
            VFilesBreadcrumbs(
                segments = state.breadcrumbs,
                onNavigate = onBreadcrumb,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.subtitle.isNotEmpty()) {
                Text(
                    state.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (state.selectedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.file_list_select_title_format, state.selectedCount),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (state.picker) {
                        FilledTonalButton(onClick = {
                            onSelection(FileBrowserSelectionCommand.PICK)
                        }) { Text(stringResource(R.string.select)) }
                    } else {
                        if (!state.selectedReadOnly) {
                            OutlinedButton(onClick = {
                                onSelection(FileBrowserSelectionCommand.CUT)
                            }) { Text(stringResource(R.string.cut)) }
                        }
                        OutlinedButton(onClick = {
                            onSelection(FileBrowserSelectionCommand.COPY)
                        }) {
                            Text(stringResource(if (state.selectedArchivePaths)
                                R.string.file_list_select_action_extract else R.string.copy))
                        }
                        if (!state.selectedReadOnly) {
                            OutlinedButton(onClick = {
                                onSelection(FileBrowserSelectionCommand.DELETE)
                            }) { Text(stringResource(R.string.delete)) }
                        }
                        if (state.selectedArchiveFiles) {
                            OutlinedButton(onClick = {
                                onSelection(FileBrowserSelectionCommand.EXTRACT)
                            }) { Text(stringResource(R.string.file_list_select_action_extract)) }
                        }
                        if (!state.currentReadOnly) {
                            OutlinedButton(onClick = {
                                onSelection(FileBrowserSelectionCommand.ARCHIVE)
                            }) { Text(stringResource(R.string.file_list_select_action_archive)) }
                        }
                        OutlinedButton(onClick = {
                            onSelection(FileBrowserSelectionCommand.SHARE)
                        }) { Text(stringResource(R.string.share)) }
                    }
                    if (!state.picker || state.pickerCanSelectMultiple) {
                        TextButton(onClick = {
                            onSelection(FileBrowserSelectionCommand.SELECT_ALL)
                        }) { Text(stringResource(R.string.select_all)) }
                    }
                    TextButton(onClick = {
                        onSelection(FileBrowserSelectionCommand.CLEAR)
                    }) { Text(stringResource(R.string.close)) }
                }
            } else if (state.pasteCount > 0 && !state.picker) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(
                            if (state.pasteIsCopy) R.string.file_list_paste_copy_title_format
                            else R.string.file_list_paste_move_title_format,
                            state.pasteCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    FilledTonalButton(
                        enabled = !state.currentReadOnly,
                        onClick = { onSelection(FileBrowserSelectionCommand.PASTE) }
                    ) { Text(stringResource(R.string.paste)) }
                    TextButton(onClick = {
                        onSelection(FileBrowserSelectionCommand.CANCEL_PASTE)
                    }) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
