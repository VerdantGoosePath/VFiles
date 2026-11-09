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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
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
    /** Index of the folder on screen; the trail may continue past it after navigating up. */
    val currentIndex: Int = -1,
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
            BrowserBreadcrumbs(
                state.breadcrumbs,
                state.currentIndex.takeIf { it in state.breadcrumbs.indices }
                    ?: state.breadcrumbs.lastIndex,
                onBreadcrumb
            )
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Read-only belongs to the folder: state it once here, not on every row.
                if (state.currentReadOnly && !state.picker) BrowserReadOnlyLabel()
            }
            BrowserViewTypeToggle(state.viewType, onViewType)
        }
    }
}

@Composable
private fun BrowserReadOnlyLabel(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            stringResource(R.string.file_browser_badge_read_only),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * Provider order comes from the path trail; never parse '/' out of user-visible names. After
 * navigating up the trail keeps the folders below the current one: they stay tappable but read
 * as secondary, and the current crumb is always scrolled into view.
 */
@Composable
private fun BrowserBreadcrumbs(
    breadcrumbs: List<BreadcrumbUi>,
    currentIndex: Int,
    onBreadcrumb: (String) -> Unit
) {
    val scroll = rememberScrollState(initial = Int.MAX_VALUE)
    var viewportWidth by remember { mutableIntStateOf(0) }
    var currentBounds by remember { mutableStateOf(IntRange.EMPTY) }
    val peek = with(LocalDensity.current) { 56.dp.roundToPx() }
    LaunchedEffect(currentIndex, breadcrumbs.size, currentBounds, viewportWidth) {
        if (viewportWidth == 0 || currentBounds.isEmpty()) return@LaunchedEffect
        // Show the current crumb's end plus a hint of any forward crumbs after it.
        val trailing = if (currentIndex < breadcrumbs.lastIndex) peek else 0
        val target = (currentBounds.last + trailing - viewportWidth)
            .coerceAtLeast(0).coerceAtMost(scroll.maxValue)
        val start = currentBounds.first
        val value = if (start < target) start else target
        if (value != scroll.value) scroll.animateScrollTo(value)
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, segment ->
            if (index > 0) {
                Icon(
                    painterResource(R.drawable.arrow_end_icon_white_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (index > currentIndex) MaterialTheme.colorScheme.outlineVariant
                        else MaterialTheme.colorScheme.outline
                )
            }
            val current = index == currentIndex
            val forward = index > currentIndex
            TextButton(
                onClick = { onBreadcrumb(segment.id) },
                enabled = !current,
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                modifier = Modifier
                    .semantics { selected = current }
                    .then(
                        if (current) Modifier.onPlaced {
                            val x = it.positionInParent().x.toInt()
                            currentBounds = x..(x + it.size.width)
                        } else Modifier
                    )
            ) {
                Text(
                    segment.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        current -> MaterialTheme.colorScheme.onSurface
                        forward -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
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
    // A short, fixed set of options: open fully so nothing hides below a half-expanded state.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
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
/** One menu row; [checked] non-null renders a checkable item. */
internal class BrowserMenuEntry(
    val title: String,
    val icon: Int? = null,
    val destructive: Boolean = false,
    val checked: Boolean? = null,
    val onClick: () -> Unit
)

/**
 * A single expressive menu surface: related commands are separated by inset dividers rather
 * than split into floating groups, so the menu reads as one continuous, gap-free container.
 */
@Composable
internal fun BrowserMenuSections(sections: List<List<BrowserMenuEntry>>) {
    val visible = sections.filter { it.isNotEmpty() }
    val count = visible.sumOf { it.size }
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
        var position = 0
        visible.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            section.forEach { entry ->
                val shapes = MenuDefaults.itemShape(position++, count)
                val color = if (entry.destructive) MaterialTheme.colorScheme.error
                    else androidx.compose.ui.graphics.Color.Unspecified
                val leading: (@Composable () -> Unit)? = entry.icon?.let { icon ->
                    {
                        Icon(
                            painterResource(icon), null,
                            tint = if (entry.destructive) MaterialTheme.colorScheme.error
                                else androidx.compose.material3.LocalContentColor.current
                        )
                    }
                }
                if (entry.checked != null) {
                    CheckableDropdownMenuItem(
                        checked = entry.checked,
                        onCheckedChange = { entry.onClick() },
                        text = { Text(entry.title, color = color) },
                        shapes = shapes,
                        leadingIcon = leading,
                        checkedLeadingIcon = {
                            Icon(painterResource(R.drawable.check_icon_control_normal_24dp), null)
                        }
                    )
                } else {
                    DropdownMenuItem(
                        onClick = entry.onClick,
                        text = { Text(entry.title, color = color) },
                        shape = shapes.shape,
                        leadingIcon = leading
                    )
                }
            }
        }
    }
}

/**
 * Folder overflow as one expressive menu: navigation, view, sharing and shortcuts are divided
 * sections of a single surface so the long list scans quickly ([C17]).
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
            val sections = buildList {
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
            }.map { section ->
                section.map { (action, icon) ->
                    BrowserMenuEntry(
                        title = stringResource(folderActionTitle(action)),
                        icon = icon,
                        checked = if (action == FileBrowserFolderAction.SHOW_HIDDEN_FILES) {
                            state.showHiddenFiles
                        } else null,
                        onClick = { open = false; onAction(action) }
                    )
                }
            }
            BrowserMenuSections(sections)
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
