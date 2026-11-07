@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.components.VFilesListDefaults

/**
 * A scalar snapshot of mutable file/provider LiveData. File operation authority remains with
 * FileListViewModel and FileListFragment; composables only render it and dispatch intents.
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
    val canOpenInTerminal: Boolean = false,
    val picker: Boolean = false,
    val pickerCanSelectMultiple: Boolean = false,
    val pasteCount: Int = 0,
    val pasteIsCopy: Boolean = false
) {
    val title: String
        get() = breadcrumbs.lastOrNull()?.label.orEmpty()
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
 * The folder is the hero of the screen: an emphasized headline with its item count, the
 * provider trail beneath it and the view controls on the same baseline. It scrolls with the
 * files so a phone spends its height on content, while the search app bar stays reachable.
 */
@Composable
internal fun BrowserLocationHeader(
    state: FileBrowserChromeState,
    onBreadcrumb: (String) -> Unit,
    onViewType: (FileViewType) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            state.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() }
        )
        if (state.breadcrumbs.size > 1) {
            BrowserBreadcrumbs(state.breadcrumbs, onBreadcrumb)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = onOpenSort,
                label = { Text(sortLabel(state.sortOptions), maxLines = 1) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.file_browser_icon_sort),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            Text(
                state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            BrowserViewTypeToggle(state.viewType, onViewType)
        }
    }
}

/** Provider order comes from the path trail; never parse '/' out of user-visible names. */
@Composable
private fun BrowserBreadcrumbs(
    breadcrumbs: List<BreadcrumbUi>,
    onBreadcrumb: (String) -> Unit
) {
    // Start scrolled to the end so the current folder's parents are visible in deep trees.
    val scroll = rememberScrollState(initial = Int.MAX_VALUE)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, segment ->
            if (index > 0) {
                Icon(
                    painterResource(R.drawable.arrow_end_icon_white_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            val current = index == breadcrumbs.lastIndex
            TextButton(
                onClick = { onBreadcrumb(segment.id) },
                enabled = !current,
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                modifier = Modifier.semantics { selected = current }
            ) {
                Text(
                    segment.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (current) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary,
                    style = if (current) MaterialTheme.typography.labelLargeEmphasized
                        else MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** Connected toggle pair: one control, two mutually exclusive layouts ([C21]). */
@Composable
private fun BrowserViewTypeToggle(viewType: FileViewType, onViewType: (FileViewType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        listOf(FileViewType.LIST, FileViewType.GRID).forEachIndexed { index, type ->
            val label = stringResource(
                if (type == FileViewType.LIST) R.string.file_list_action_view_list
                else R.string.file_list_action_view_grid
            )
            ToggleButton(
                checked = viewType == type,
                onCheckedChange = { if (it) onViewType(type) },
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                contentPadding = ToggleButtonDefaults.contentPaddingFor(
                    ToggleButtonDefaults.size, hasStartIcon = false
                ),
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    selected = viewType == type
                }
            ) {
                Icon(
                    painterResource(
                        if (type == FileViewType.LIST) R.drawable.file_browser_icon_list
                        else R.drawable.file_browser_icon_grid
                    ),
                    contentDescription = label,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun sortLabel(options: FileSortOptions): String {
    val by = stringResource(sortByLabel(options.by))
    return by + if (options.order == FileSortOptions.Order.ASCENDING) " ↑" else " ↓"
}

private fun sortByLabel(by: FileSortOptions.By): Int = when (by) {
    FileSortOptions.By.NAME -> R.string.file_list_action_sort_by_name
    FileSortOptions.By.TYPE -> R.string.file_list_action_sort_by_type
    FileSortOptions.By.SIZE -> R.string.file_list_action_sort_by_size
    FileSortOptions.By.LAST_MODIFIED -> R.string.file_list_action_sort_by_last_modified
}

/** Sort and visibility options as native segmented groups in a dismissible sheet. */
@Composable
internal fun BrowserSortSheet(
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
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.file_list_action_view_sort),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)
                    .semantics { heading() }
            )
            BrowserSheetSectionLabel(stringResource(R.string.file_browser_sort_by))
            // Segmented seams use SegmentedGap, not the section spacing, to read as one group.
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                val entries = FileSortOptions.By.entries
                entries.forEachIndexed { index, by ->
                    val selected = state.sortOptions.by == by
                    SegmentedListItem(
                        selected = selected,
                        onClick = { onSortBy(by) },
                        shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                        colors = VFilesListDefaults.sheetSegmentedColors(),
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        },
                        content = { Text(stringResource(sortByLabel(by))) }
                    )
                }
            }
            BrowserSheetSectionLabel(stringResource(R.string.file_browser_sort_options))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                BrowserSwitchRow(
                    stringResource(R.string.file_list_action_sort_order_ascending),
                    state.sortOptions.order == FileSortOptions.Order.ASCENDING,
                    index = 0, count = 4
                ) { checked ->
                    onSortOrder(
                        if (checked) FileSortOptions.Order.ASCENDING
                        else FileSortOptions.Order.DESCENDING
                    )
                }
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
private fun BrowserSheetSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
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
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = VFilesListDefaults.sheetSegmentedColors(),
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        content = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    )
}

/**
 * [C34] Contextual selection actions in a vibrant floating toolbar near the thumb. The most
 * frequent commands are visible; rarer ones live in its overflow so labels never wrap.
 */
@Composable
internal fun BrowserSelectionToolbar(
    state: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = state.commandContext().availableCommands()
    var moreOpen by remember { mutableStateOf(false) }
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
        if (FileBrowserSelectionCommand.DELETE in commands) {
            BrowserToolbarAction(R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete)) {
                onSelection(FileBrowserSelectionCommand.DELETE)
            }
        }
        val overflow = listOf(
            FileBrowserSelectionCommand.EXTRACT, FileBrowserSelectionCommand.ARCHIVE
        ).filter { it in commands }
        if (overflow.isNotEmpty()) {
            Box {
                BrowserToolbarAction(
                    R.drawable.file_browser_icon_more,
                    stringResource(R.string.file_browser_more_actions)
                ) { moreOpen = true }
                DropdownMenuPopup(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        overflow.forEachIndexed { index, command ->
                            val (icon, title) = when (command) {
                                FileBrowserSelectionCommand.EXTRACT ->
                                    R.drawable.extract_icon_control_normal_24dp to
                                        R.string.file_list_select_action_extract
                                else -> R.drawable.archive_icon_control_normal_24dp to
                                    R.string.file_list_select_action_archive
                            }
                            DropdownMenuItem(
                                onClick = { moreOpen = false; onSelection(command) },
                                text = { Text(stringResource(title)) },
                                shape = MenuDefaults.itemShape(index, overflow.size).shape,
                                leadingIcon = { Icon(painterResource(icon), null) }
                            )
                        }
                    }
                }
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
    val pasteLabel = stringResource(R.string.file_browser_paste_here)
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
                    if (state.pasteIsCopy) R.string.file_list_paste_copy_title_format
                    else R.string.file_list_paste_move_title_format,
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
/**
 * Folder overflow as an expressive grouped menu: navigation, view, sharing and shortcuts are
 * separate groups so the long list scans quickly ([C17]).
 */
@Composable
internal fun BrowserOverflowMenu(
    state: FileBrowserChromeState,
    onAction: (FileBrowserFolderAction) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(R.drawable.file_browser_icon_more),
                contentDescription = stringResource(R.string.file_browser_more_options)
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            val groups = buildList {
                add(listOf(
                    FileBrowserFolderAction.NAVIGATE_UP to R.drawable.arrow_up_icon_control_normal_24dp,
                    FileBrowserFolderAction.NAVIGATE_TO to R.drawable.navigate_to_icon_control_normal_24dp,
                    FileBrowserFolderAction.NEW_TASK to R.drawable.new_window_icon_control_normal_24dp
                ))
                add(listOf(
                    FileBrowserFolderAction.REFRESH to R.drawable.refresh_icon_control_normal_24dp,
                    FileBrowserFolderAction.SELECT_ALL to R.drawable.select_all_icon_control_normal_24dp,
                    FileBrowserFolderAction.SHOW_HIDDEN_FILES to R.drawable.visibility_icon_control_normal_24dp
                ))
                add(buildList {
                    add(FileBrowserFolderAction.SHARE to R.drawable.share_icon_control_normal_24dp)
                    add(FileBrowserFolderAction.COPY_PATH to R.drawable.copy_path_icon_control_normal_24dp)
                    if (state.canOpenInTerminal) {
                        add(FileBrowserFolderAction.OPEN_IN_TERMINAL to
                            R.drawable.terminal_icon_control_normal_24dp)
                    }
                })
                add(listOf(
                    FileBrowserFolderAction.ADD_BOOKMARK to R.drawable.bookmark_icon_control_normal_24dp,
                    FileBrowserFolderAction.CREATE_SHORTCUT to R.drawable.shortcut_icon_control_normal_24dp
                ))
            }
            groups.forEachIndexed { groupIndex, group ->
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                    group.forEachIndexed { index, (action, icon) ->
                        val title = stringResource(folderActionTitle(action))
                        if (action == FileBrowserFolderAction.SHOW_HIDDEN_FILES) {
                            CheckableDropdownMenuItem(
                                checked = state.showHiddenFiles,
                                onCheckedChange = { open = false; onAction(action) },
                                text = { Text(title) },
                                shapes = MenuDefaults.itemShape(index, group.size),
                                leadingIcon = { Icon(painterResource(icon), null) },
                                checkedLeadingIcon = {
                                    Icon(painterResource(R.drawable.check_icon_control_normal_24dp), null)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                onClick = { open = false; onAction(action) },
                                text = { Text(title) },
                                shape = MenuDefaults.itemShape(index, group.size).shape,
                                leadingIcon = { Icon(painterResource(icon), null) }
                            )
                        }
                    }
                }
                if (groupIndex != groups.lastIndex) {
                    Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                }
            }
        }
    }
}

private fun folderActionTitle(action: FileBrowserFolderAction): Int = when (action) {
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
