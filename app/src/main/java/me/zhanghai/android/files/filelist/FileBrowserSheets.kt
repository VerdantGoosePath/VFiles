@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesBottomSheet
import me.zhanghai.android.files.compose.components.VFilesSheetAction
import me.zhanghai.android.files.compose.components.VFilesSheetColumn
import me.zhanghai.android.files.compose.components.VFilesSheetGroup
import me.zhanghai.android.files.compose.components.VFilesSheetHeader
import me.zhanghai.android.files.compose.components.VFilesSheetSectionLabel
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.provider.archive.isArchivePath

/**
 * View and sort options. "Only for this folder" comes first and starts on, so a change made
 * here stays with the folder on screen unless the user turns it off. The sort direction lives
 * on the selected key: tapping it again reverses the order, picking another key keeps it.
 */
@Composable
internal fun BrowserViewSortSheet(
    state: FileBrowserChromeState,
    actions: FileBrowserScreenActions,
    onDismiss: () -> Unit
) {
    var onlyThisFolder by rememberSaveable { mutableStateOf(true) }
    val forFolder = onlyThisFolder || state.pathSpecificSort
    fun apply(change: () -> Unit) {
        if (forFolder && !state.pathSpecificSort) actions.onPathSpecificSort(true)
        change()
    }
    VFilesBottomSheet(onDismissRequest = onDismiss) {
        VFilesSheetColumn {
            VFilesSheetHeader(
                R.drawable.file_browser_icon_sort, stringResource(R.string.file_list_action_view_sort)
            )
            VFilesSheetGroup {
                SheetSwitchAction(
                    icon = R.drawable.folder_icon_control_normal_24dp,
                    title = stringResource(R.string.file_browser_only_this_folder),
                    supporting = stringResource(
                        if (forFolder) R.string.file_browser_only_this_folder_on
                        else R.string.file_browser_only_this_folder_off
                    ),
                    checked = forFolder, index = 0, count = 1
                ) { checked ->
                    onlyThisFolder = checked
                    actions.onPathSpecificSort(checked)
                }
            }
            VFilesSheetSectionLabel(stringResource(R.string.file_browser_sort_by))
            VFilesSheetGroup {
                val entries = FileSortOptions.By.entries
                entries.forEachIndexed { index, by ->
                    val selected = state.sortOptions.by == by
                    val ascending = state.sortOptions.order == FileSortOptions.Order.ASCENDING
                    val direction = stringResource(sortDirectionLabel(state.sortOptions))
                    val reverseHint = stringResource(R.string.file_browser_sort_reverse_hint)
                    val rotation by animateFloatAsState(
                        if (ascending) 0f else 180f, MaterialTheme.motionScheme.defaultSpatialSpec()
                    )
                    VFilesSheetAction(
                        icon = sortByIcon(by),
                        title = stringResource(sortByLabel(by)),
                        supporting = if (selected) direction else null,
                        index = index, count = entries.size,
                        selected = selected,
                        onClick = {
                            apply {
                                if (selected) {
                                    actions.onSortOrder(
                                        if (ascending) FileSortOptions.Order.DESCENDING
                                        else FileSortOptions.Order.ASCENDING
                                    )
                                } else actions.onSortBy(by)
                            }
                        },
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            this.selected = selected
                            if (selected) stateDescription = "$direction. $reverseHint"
                        },
                        trailing = if (selected) {
                            {
                                Icon(
                                    painterResource(R.drawable.browser_icon_arrow_upward),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate(rotation)
                                )
                            }
                        } else null
                    )
                }
            }
            if (state.viewTogglePlacement.inSheet) {
                VFilesSheetSectionLabel(stringResource(R.string.file_browser_layout))
                BrowserViewTypeToggle(
                    state.viewType,
                    { type -> apply { actions.onViewType(type) } },
                    modifier = Modifier.fillMaxWidth(),
                    fill = true
                )
            }
            VFilesSheetSectionLabel(stringResource(R.string.file_browser_options))
            VFilesSheetGroup {
                SheetSwitchAction(
                    icon = R.drawable.browser_icon_folders_first,
                    title = stringResource(R.string.file_list_action_sort_directories_first),
                    checked = state.sortOptions.isDirectoriesFirst, index = 0, count = 2
                ) { checked -> apply { actions.onDirectoriesFirst(checked) } }
                SheetSwitchAction(
                    icon = R.drawable.visibility_icon_control_normal_24dp,
                    title = stringResource(R.string.file_list_action_show_hidden_files),
                    checked = state.showHiddenFiles, index = 1, count = 2,
                    onChange = actions::onShowHiddenFiles
                )
            }
        }
    }
}

@Composable
private fun SheetSwitchAction(
    icon: Int,
    title: String,
    checked: Boolean,
    index: Int,
    count: Int,
    supporting: String? = null,
    onChange: (Boolean) -> Unit
) {
    // The row is the only input owner; the switch is a visual state indicator.
    VFilesSheetAction(
        icon = icon, title = title, supporting = supporting, index = index, count = count,
        onClick = { onChange(!checked) },
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        trailing = {
            Switch(checked = checked, onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { })
        }
    )
}

private class SheetEntry(
    val icon: Int,
    val title: Int,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val checked: Boolean? = null,
    val onClick: () -> Unit
)

@Composable
private fun SheetSections(
    sections: List<List<SheetEntry>>,
    dismiss: (then: () -> Unit) -> Unit
) {
    sections.filter { it.isNotEmpty() }.forEach { section ->
        VFilesSheetGroup {
            section.forEachIndexed { index, entry ->
                if (entry.checked != null) {
                    SheetSwitchAction(
                        entry.icon, stringResource(entry.title), entry.checked, index,
                        section.size
                    ) { entry.onClick() }
                } else {
                    VFilesSheetAction(
                        icon = entry.icon, title = stringResource(entry.title), index = index,
                        count = section.size, enabled = entry.enabled,
                        destructive = entry.destructive,
                        onClick = { dismiss(entry.onClick) }
                    )
                }
            }
        }
    }
}

/** Folder commands in a sheet: grouped by intent, with the view and sort options one tap away. */
@Composable
internal fun BrowserFolderSheet(
    state: FileBrowserChromeState,
    onAction: (FileBrowserFolderAction) -> Unit,
    onOpenViewSort: () -> Unit,
    onDismiss: () -> Unit
) {
    VFilesBottomSheet(onDismissRequest = onDismiss) { controller ->
        VFilesSheetColumn {
            VFilesSheetHeader(
                R.drawable.folder_icon_control_normal_24dp, state.title,
                subtitle = state.subtitle.takeIf { it.isNotEmpty() }
            )
            val act = { action: FileBrowserFolderAction -> { onAction(action) } }
            val sections = buildList {
                add(buildList {
                    add(SheetEntry(R.drawable.tune_icon_control_normal_24dp,
                        R.string.file_list_action_view_sort, onClick = onOpenViewSort))
                    add(SheetEntry(R.drawable.refresh_icon_control_normal_24dp, R.string.refresh,
                        onClick = act(FileBrowserFolderAction.REFRESH)))
                    if (!state.picker || state.pickerCanSelectMultiple) {
                        add(SheetEntry(R.drawable.select_all_icon_control_normal_24dp,
                            R.string.select_all, onClick = act(FileBrowserFolderAction.SELECT_ALL)))
                    }
                    add(SheetEntry(R.drawable.visibility_icon_control_normal_24dp,
                        R.string.file_list_action_show_hidden_files, checked = state.showHiddenFiles,
                        onClick = act(FileBrowserFolderAction.SHOW_HIDDEN_FILES)))
                })
                add(buildList {
                    if (!state.showUpButton) {
                        add(SheetEntry(R.drawable.arrow_up_icon_control_normal_24dp,
                            R.string.file_list_action_navigate_up, enabled = state.canNavigateUp,
                            onClick = act(FileBrowserFolderAction.NAVIGATE_UP)))
                    }
                    add(SheetEntry(R.drawable.navigate_to_icon_control_normal_24dp,
                        R.string.file_list_action_navigate_to,
                        onClick = act(FileBrowserFolderAction.NAVIGATE_TO)))
                    if (!state.picker) {
                        add(SheetEntry(R.drawable.new_window_icon_control_normal_24dp,
                            R.string.file_list_action_new_task,
                            onClick = act(FileBrowserFolderAction.NEW_TASK)))
                    }
                })
                if (!state.picker) {
                    add(buildList {
                        add(SheetEntry(R.drawable.share_icon_control_normal_24dp, R.string.share,
                            onClick = act(FileBrowserFolderAction.SHARE)))
                        add(SheetEntry(R.drawable.copy_path_icon_control_normal_24dp,
                            R.string.file_list_action_copy_path,
                            onClick = act(FileBrowserFolderAction.COPY_PATH)))
                        if (state.canOpenInTerminal) {
                            add(SheetEntry(R.drawable.terminal_icon_control_normal_24dp,
                                R.string.file_list_action_open_in_terminal,
                                onClick = act(FileBrowserFolderAction.OPEN_IN_TERMINAL)))
                        }
                    })
                    add(listOf(
                        SheetEntry(R.drawable.bookmark_icon_control_normal_24dp,
                            R.string.file_list_action_add_bookmark,
                            onClick = act(FileBrowserFolderAction.ADD_BOOKMARK)),
                        SheetEntry(R.drawable.shortcut_icon_control_normal_24dp,
                            R.string.file_list_action_create_shortcut,
                            onClick = act(FileBrowserFolderAction.CREATE_SHORTCUT))
                    ))
                }
            }
            SheetSections(sections) { then -> controller.dismiss(then) }
        }
    }
}

/** Per-file actions; the header names the file so the sheet never loses its subject. */
@Composable
internal fun FileBrowserItemSheet(
    file: FileItem,
    selected: Boolean,
    selectable: Boolean,
    picker: Boolean,
    onAction: (FileBrowserItemAction) -> Unit,
    onDismiss: () -> Unit
) {
    val directory = file.attributes.isDirectory
    val readOnly = file.path.fileSystem.isReadOnly
    val archivePath = file.path.isArchivePath
    val context = LocalContext.current
    VFilesBottomSheet(onDismissRequest = onDismiss) { controller ->
        VFilesSheetColumn {
            VFilesSheetHeader(
                if (directory) R.drawable.file_browser_icon_folder else file.mimeType.iconRes,
                file.name,
                subtitle = fileSupportingText(file, context)
            )
            val act = { action: FileBrowserItemAction -> { onAction(action) } }
            val sections = buildList {
                add(buildList {
                    if (selectable) {
                        add(SheetEntry(R.drawable.check_icon_control_normal_24dp,
                            if (selected) R.string.file_browser_deselect else R.string.select,
                            onClick = act(FileBrowserItemAction.SELECT)))
                    }
                    if (!directory) {
                        add(SheetEntry(R.drawable.open_with_icon_control_normal_24dp,
                            R.string.file_item_action_open_with,
                            onClick = act(FileBrowserItemAction.OPEN_WITH)))
                    }
                    if (!picker && !readOnly && !archivePath && !directory) {
                        add(SheetEntry(R.drawable.edit_icon, R.string.edit,
                            onClick = act(FileBrowserItemAction.EDIT_WITH)))
                    }
                })
                if (!picker) {
                    add(buildList {
                        if (!readOnly) {
                            add(SheetEntry(R.drawable.cut_icon_control_normal_24dp, R.string.cut,
                                onClick = act(FileBrowserItemAction.CUT)))
                        }
                        add(if (archivePath) {
                            SheetEntry(R.drawable.extract_icon_control_normal_24dp,
                                R.string.file_item_action_extract,
                                onClick = act(FileBrowserItemAction.COPY))
                        } else {
                            SheetEntry(R.drawable.copy_icon_control_normal_24dp, R.string.copy,
                                onClick = act(FileBrowserItemAction.COPY))
                        })
                        if (!readOnly) {
                            add(SheetEntry(R.drawable.rename_icon_control_normal_24dp,
                                R.string.rename, onClick = act(FileBrowserItemAction.RENAME)))
                            add(SheetEntry(R.drawable.delete_icon_control_normal_24dp,
                                R.string.delete, destructive = true,
                                onClick = act(FileBrowserItemAction.DELETE)))
                        }
                    })
                    add(buildList {
                        if (file.isArchiveFile) {
                            add(SheetEntry(R.drawable.extract_icon_control_normal_24dp,
                                R.string.file_item_action_extract,
                                onClick = act(FileBrowserItemAction.EXTRACT)))
                        }
                        if (!archivePath) {
                            add(SheetEntry(R.drawable.archive_icon_control_normal_24dp,
                                R.string.file_item_action_archive,
                                onClick = act(FileBrowserItemAction.ARCHIVE)))
                        }
                        add(SheetEntry(R.drawable.share_icon_control_normal_24dp, R.string.share,
                            onClick = act(FileBrowserItemAction.SHARE)))
                        add(SheetEntry(R.drawable.copy_path_icon_control_normal_24dp,
                            R.string.file_item_action_copy_path,
                            onClick = act(FileBrowserItemAction.COPY_PATH)))
                    })
                    add(buildList {
                        if (directory) {
                            add(SheetEntry(R.drawable.bookmark_icon_control_normal_24dp,
                                R.string.file_item_action_add_bookmark,
                                onClick = act(FileBrowserItemAction.BOOKMARK)))
                        }
                        add(SheetEntry(R.drawable.shortcut_icon_control_normal_24dp,
                            R.string.file_item_action_create_shortcut,
                            onClick = act(FileBrowserItemAction.SHORTCUT)))
                        add(SheetEntry(R.drawable.information_icon_control_normal_24dp,
                            R.string.file_item_action_properties,
                            onClick = act(FileBrowserItemAction.PROPERTIES)))
                    })
                }
            }
            SheetSections(sections) { then -> controller.dismiss(then) }
        }
    }
}
