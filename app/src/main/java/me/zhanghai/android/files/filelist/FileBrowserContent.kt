package me.zhanghai.android.files.filelist

import android.text.TextUtils
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.dispose
import coil.load
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesMessageState
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted

/**
 * File data is loaded by the existing provider LiveData; this snapshot only contains metadata
 * already obtained on its IO dispatcher. Selection is copied to an immutable path set because
 * FileItemSet is a mutable LiveData value whose identity is not changed on selection.
 */
data class FileBrowserContentState(
    val pathKey: String = "",
    val files: List<FileItem> = emptyList(),
    val selectedPaths: Set<Path> = emptySet(),
    val sort: FileSortOptions = FileSortOptions(
        FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true
    ),
    val viewType: FileViewType = FileViewType.LIST,
    val searching: Boolean = false,
    val showHidden: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val pickOptions: PickOptions? = null,
    val nameEllipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END
)

enum class FileBrowserItemAction {
    OPEN, SELECT, OPEN_WITH, EDIT_WITH, CUT, COPY, DELETE, RENAME,
    EXTRACT, ARCHIVE, SHARE, COPY_PATH, BOOKMARK, SHORTCUT, PROPERTIES
}

/**
 * Lazy Compose rows and grid; stable path keys + content types, off-main sorting and no provider IO
 * during composition. SaveableStateProvider retains each path/mode scroll position across navigation.
 * Native Android ImageViews remain solely as Coil thumbnail targets to preserve app fetchers/cache.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VFilesBrowserContent(
    state: FileBrowserContentState,
    onRefresh: () -> Unit,
    onItemAction: (FileItem, FileBrowserItemAction) -> Unit,
    onRangeSelect: (List<FileItem>) -> Unit,
    onListReachedTop: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val visible by produceState(
        initialValue = emptyList<FileItem>(),
        state.pathKey, state.files, state.sort, state.showHidden, state.searching
    ) {
        value = withContext(Dispatchers.Default) {
            val files = if (state.showHidden) state.files else state.files.filterNot { it.isHidden }
            if (state.searching) files else files.sortedWith(state.sort.createComparator())
        }
    }
    val savedState = rememberSaveableStateHolder()
    PullToRefreshBox(
        isRefreshing = state.loading && state.files.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.error != null && visible.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
                    }
                }
            } else if (state.loading && state.files.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.loading && visible.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(48.dp))
                    }
                }
                state.error != null && visible.isEmpty() -> {
                    VFilesMessageState(
                        title = stringResource(R.string.error),
                        detail = state.error,
                        actionLabel = stringResource(R.string.refresh),
                        onAction = onRefresh,
                        isError = true
                    )
                }
                !state.loading && visible.isEmpty() -> {
                    VFilesMessageState(
                        title = if (state.searching) stringResource(R.string.search)
                            else stringResource(R.string.file_list_empty),
                        detail = stringResource(if (state.searching) R.string.file_browser_search_no_results else R.string.file_browser_empty_description),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = onRefresh
                    )
                }
                else -> {
                    // Keep list and grid scroll states independently, for every visited provider path.
                    savedState.SaveableStateProvider(state.pathKey + ":" + state.viewType.name) {
                        if (state.viewType == FileViewType.LIST) {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(visible, key = { _, file -> file.path.toString() },
                                    contentType = { _, file ->
                                        if (file.attributes.isDirectory) "directory" else "file"
                                    }) { index, file ->
                                    FileBrowserItem(
                                        file = file,
                                        grid = false,
                                        selected = file.path in state.selectedPaths,
                                        hasSelection = state.selectedPaths.isNotEmpty(),
                                        pickOptions = state.pickOptions,
                                        ellipsize = state.nameEllipsize,
                                        onAction = { onItemAction(file, it) },
                                        onKeyNavigation = { key, shift ->
                                            val next = index + if (key == Key.DirectionDown) 1 else -1
                                            when {
                                                shift && next in visible.indices -> {
                                                    onRangeSelect(visible.subList(
                                                        minOf(index, next), maxOf(index, next) + 1
                                                    ))
                                                    false // Allow native focus to advance.
                                                }
                                                !shift && index == 0 && key == Key.DirectionUp ->
                                                    onListReachedTop()
                                                else -> false
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 164.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gridItemsIndexed(visible, key = { _, file -> file.path.toString() },
                                    contentType = { _, file ->
                                        if (file.attributes.isDirectory) "directory" else "file"
                                    }) { index, file ->
                                    FileBrowserItem(
                                        file = file,
                                        grid = true,
                                        selected = file.path in state.selectedPaths,
                                        hasSelection = state.selectedPaths.isNotEmpty(),
                                        pickOptions = state.pickOptions,
                                        ellipsize = state.nameEllipsize,
                                        onAction = { onItemAction(file, it) },
                                        onKeyNavigation = { key, shift ->
                                            val next = index + if (key == Key.DirectionDown) 1 else -1
                                            if (shift && next in visible.indices) {
                                                onRangeSelect(visible.subList(
                                                    minOf(index, next), maxOf(index, next) + 1
                                                ))
                                                false
                                            } else if (!shift && index == 0 && key == Key.DirectionUp) {
                                                onListReachedTop()
                                            } else false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun canSelectBrowserFile(file: FileItem, pick: PickOptions?): Boolean {
    if (pick == null) return true
    return when (pick.mode) {
        PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
            !file.attributes.isDirectory && pick.mimeTypes.any { it.match(file.mimeType) }
        PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
    }
}

/** Context menu actions mirror res/menu/file_item.xml and use the same fragment listener API. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserItem(
    file: FileItem,
    grid: Boolean,
    selected: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    ellipsize: TextUtils.TruncateAt,
    onAction: (FileBrowserItemAction) -> Unit,
    onKeyNavigation: (Key, Boolean) -> Boolean
) {
    val isDirectory = file.attributes.isDirectory
    val selectable = canSelectBrowserFile(file, pickOptions)
    val enabled = selectable || isDirectory
    val readOnly = file.path.fileSystem.isReadOnly
    val archivePath = file.path.isArchivePath
    var menuExpanded by remember(file.path) { mutableStateOf(false) }
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    Surface(
        color = bg,
        shape = if (grid) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                    onKeyNavigation(event.key, event.isShiftPressed)
                } else false
            }
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    if (hasSelection && selectable) onAction(FileBrowserItemAction.SELECT)
                    else onAction(FileBrowserItemAction.OPEN)
                },
                onLongClick = {
                    if (selectable) onAction(FileBrowserItemAction.SELECT)
                    else if (isDirectory) onAction(FileBrowserItemAction.OPEN)
                }
            )
    ) {
        if (grid) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileBrowserThumbnail(file, Modifier.size(88.dp))
                    FileBrowserItemMenu(file, readOnly, archivePath,
                        pickOptions != null, menuExpanded, { menuExpanded = it }, onAction)
                }
                Text(
                    file.name, style = MaterialTheme.typography.titleSmall,
                    color = contentColor, maxLines = 2, overflow = ellipsize.toComposeOverflow()
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selected) Checkbox(checked = true, onCheckedChange = null)
                    else if (selectable) TextButton(
                        onClick = { onAction(FileBrowserItemAction.SELECT) }
                    ) { Text(stringResource(R.string.select)) }
                    FileBrowserBadges(file)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FileBrowserThumbnail(file, Modifier.size(46.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name, style = MaterialTheme.typography.titleMedium,
                        color = contentColor, maxLines = 2, overflow = ellipsize.toComposeOverflow()
                    )
                    if (!isDirectory) {
                        val context = LocalContext.current
                        val fileSize = file.attributes.fileSize.formatHumanReadable(context)
                        val modified = file.attributes.lastModifiedTime().toInstant()
                            .formatShort(context)
                        Text(
                            "$modified · $fileSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    FileBrowserBadges(file)
                }
                if (selected) {
                    Checkbox(checked = true, onCheckedChange = null)
                } else if (selectable) {
                    Checkbox(checked = false, onCheckedChange = {
                        onAction(FileBrowserItemAction.SELECT)
                    })
                }
                FileBrowserItemMenu(file, readOnly, archivePath,
                    pickOptions != null, menuExpanded, { menuExpanded = it }, onAction)
            }
        }
    }
}

@Composable
private fun FileBrowserThumbnail(file: FileItem, modifier: Modifier = Modifier) {
    val thumbnail = file.supportsThumbnail
    val fallbackIcon = file.mimeType.iconRes
    val surface = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    Box(modifier, contentAlignment = Alignment.Center) {
        // Coil's existing PathAttributesFetcher and custom providers remain in the image pipeline.
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(surface)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { image ->
                val request = file.path to file.attributes
                if (image.tag != request) {
                    image.dispose()
                    image.tag = request
                    image.setImageResource(fallbackIcon)
                    if (thumbnail) image.load(request) {
                        crossfade(false)
                        placeholder(fallbackIcon)
                        error(fallbackIcon)
                    }
                }
            },
            onRelease = { image -> image.dispose(); image.tag = null }
        )
    }
}

@Composable
private fun FileBrowserBadges(file: FileItem) {
    val badge = when {
        file.attributesNoFollowLinks.isSymbolicLink ->
            if (file.isSymbolicLinkBroken) R.drawable.error_badge_icon_18dp
            else R.drawable.symbolic_link_badge_icon_18dp
        file.attributesNoFollowLinks.isEncrypted() -> R.drawable.encrypted_badge_icon_18dp
        else -> null
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Icon(
                painter = painterResource(badge),
                contentDescription = if (file.attributesNoFollowLinks.isSymbolicLink)
                    stringResource(R.string.file_browser_badge_symbolic_link) else stringResource(R.string.file_browser_badge_encrypted),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (file.isArchiveFile || file.path.isArchivePath) {
            Text(
                stringResource(R.string.file_browser_badge_archive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (file.path.fileSystem.isReadOnly) {
            Text(
                stringResource(R.string.file_browser_badge_read_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileBrowserItemMenu(
    file: FileItem,
    readOnly: Boolean,
    archivePath: Boolean,
    picker: Boolean,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onAction: (FileBrowserItemAction) -> Unit
) {
    Box {
        IconButton(onClick = { onExpanded(true) }) {
            Icon(
                painterResource(R.drawable.more_vertical_icon_white_24dp),
                contentDescription = stringResource(R.string.file_browser_item_actions)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            @Composable
            fun item(id: Int, action: FileBrowserItemAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(id)) },
                    onClick = { onExpanded(false); onAction(action) }
                )
            }
            item(R.string.file_item_action_open_with, FileBrowserItemAction.OPEN_WITH)
            if (!picker) {
                if (!readOnly && !archivePath && !file.attributes.isDirectory) {
                    item(R.string.edit, FileBrowserItemAction.EDIT_WITH)
                }
                if (!readOnly) item(R.string.cut, FileBrowserItemAction.CUT)
                item(if (archivePath) R.string.file_item_action_extract else R.string.copy,
                    FileBrowserItemAction.COPY)
                if (!readOnly) {
                    item(R.string.delete, FileBrowserItemAction.DELETE)
                    item(R.string.rename, FileBrowserItemAction.RENAME)
                }
                if (file.isArchiveFile) item(R.string.file_item_action_extract, FileBrowserItemAction.EXTRACT)
                if (!archivePath) item(R.string.file_item_action_archive, FileBrowserItemAction.ARCHIVE)
                item(R.string.share, FileBrowserItemAction.SHARE)
                item(R.string.file_item_action_copy_path, FileBrowserItemAction.COPY_PATH)
                if (file.attributes.isDirectory)
                    item(R.string.file_item_action_add_bookmark, FileBrowserItemAction.BOOKMARK)
                item(R.string.file_item_action_create_shortcut, FileBrowserItemAction.SHORTCUT)
                item(R.string.file_item_action_properties, FileBrowserItemAction.PROPERTIES)
            }
        }
    }
}

private fun TextUtils.TruncateAt.toComposeOverflow(): TextOverflow = when (this) {
    TextUtils.TruncateAt.START -> TextOverflow.StartEllipsis
    TextUtils.TruncateAt.MIDDLE -> TextOverflow.MiddleEllipsis
    TextUtils.TruncateAt.MARQUEE -> TextOverflow.Clip
    else -> TextOverflow.Ellipsis
}
