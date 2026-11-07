package me.zhanghai.android.files.filelist

import android.content.res.ColorStateList
import android.text.TextUtils
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    chromeState: FileBrowserChromeState,
    hasActiveJobs: Boolean,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    onRefresh: () -> Unit,
    onItemAction: (FileItem, FileBrowserItemAction) -> Unit,
    onRangeSelect: (List<FileItem>) -> Unit,
    onListReachedTop: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Do not briefly display the previous folder's items while an asynchronous sort runs.
    // A null result means preparing, never an empty directory eligible for user actions.
    val visibleResult by key(state.pathKey, state.files, state.sort, state.showHidden, state.searching) {
        produceState<List<FileItem>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                val files = if (state.showHidden) state.files else state.files.filterNot { it.isHidden }
                if (state.searching) files else files.sortedWith(state.sort.createComparator())
            }
        }
    }
    val visible = visibleResult.orEmpty()
    val savedState = rememberSaveableStateHolder()
    // Reserve the *measured* contextual dock height. A fixed 196dp estimate clips the
    // final file at 200% font, with translated strings or the IME visible.
    val hasContextDock = (chromeState.selectedCount > 0 || chromeState.pasteCount > 0) &&
        state.pickOptions == null
    var measuredDockHeight by remember { mutableIntStateOf(0) }
    val dockHeight = with(LocalDensity.current) { measuredDockHeight.toDp() }
    val contentBottomPadding = (if (hasContextDock) {
        maxOf(120.dp, dockHeight + 16.dp)
    } else 120.dp) + if (hasActiveJobs) 76.dp else 0.dp
    Box(modifier = modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = state.loading && state.files.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                (visibleResult == null || state.loading) && visible.isEmpty() -> {
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
                            // Long file names and metadata should not span an entire
                            // expanded Android 17 window: bound the *list* but retain
                            // full-width adaptive media grids.
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(visible, key = { _, file -> file.path.toString() },
                                    contentType = { _, file ->
                                        if (file.attributes.isDirectory) "directory" else "file"
                                    }) { index, file ->
                                    FileBrowserItem(
                                        file = file,
                                        grid = false,
                                        index = index,
                                        count = visible.size,
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
                                columns = GridCells.Adaptive(minSize = 156.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentBottomPadding
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
                                        index = index,
                                        count = visible.size,
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
    // [C34] Contextual actions belong near the thumb, independent of the scrolling app bar.
    // Existing 120dp lazy-content bottom padding keeps the final item reachable above this dock.
    val dockModifier = Modifier.align(Alignment.BottomCenter)
        .onSizeChanged { measuredDockHeight = it.height }
        .navigationBarsPadding().imePadding()
        .padding(start = 16.dp, end = 16.dp, top = 12.dp,
            bottom = if (hasActiveJobs) 76.dp else 12.dp).widthIn(max = 520.dp)
    if (chromeState.selectedCount > 0 && state.pickOptions == null) {
        BrowserSelectionBar(
            state = chromeState, onSelection = onSelection, modifier = dockModifier
        )
    } else if (chromeState.pasteCount > 0 && state.pickOptions == null) {
        BrowserPasteBar(
            state = chromeState, onSelection = onSelection, modifier = dockModifier
        )
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
/**
 * Expressive [C10] list rows share an intentional segmented group shape; the selection expands
 * into a distinct tonal state instead of permanently displaying checkboxes on every file.
 * Tiles are media-first, with the action menu over the preview and no redundant "Select" button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserItem(
    file: FileItem,
    grid: Boolean,
    index: Int,
    count: Int,
    selected: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    ellipsize: TextUtils.TruncateAt,
    onAction: (FileBrowserItemAction) -> Unit,
    onKeyNavigation: (Key, Boolean) -> Boolean
) {
    val directory = file.attributes.isDirectory
    val selectable = canSelectBrowserFile(file, pickOptions)
    val enabled = selectable || directory
    val readOnly = file.path.fileSystem.isReadOnly
    val archivePath = file.path.isArchivePath
    var menuExpanded by remember(file.path) { mutableStateOf(false) }
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    val foreground = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    val shape = when {
        selected -> MaterialTheme.shapes.extraLarge
        grid || count == 1 -> MaterialTheme.shapes.large
        else -> RoundedCornerShape(
            topStart = if (index == 0) 24.dp else 6.dp,
            topEnd = if (index == 0) 24.dp else 6.dp,
            bottomStart = if (index == count - 1) 24.dp else 6.dp,
            bottomEnd = if (index == count - 1) 24.dp else 6.dp
        )
    }
    Surface(
        color = background,
        shape = shape,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                    onKeyNavigation(event.key, event.isShiftPressed)
                } else false
            }
            .combinedClickable(
                enabled = enabled,
                onClickLabel = if (hasSelection) stringResource(
                    if (selected) R.string.file_browser_deselect else R.string.select
                ) else null,
                onLongClickLabel = if (selectable) stringResource(R.string.select) else null,
                onClick = {
                    if (hasSelection && selectable) onAction(FileBrowserItemAction.SELECT)
                    else onAction(FileBrowserItemAction.OPEN)
                },
                onLongClick = if (selectable) {
                    { onAction(FileBrowserItemAction.SELECT) }
                } else null
            )
    ) {
        if (grid) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FileBrowserThumbnail(
                        file = file,
                        grid = true,
                        modifier = if (directory || !file.supportsThumbnail) Modifier.size(80.dp)
                            else Modifier.fillMaxSize()
                    )
                    if (selected || hasSelection) {
                        Box(Modifier.align(Alignment.TopStart)) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        }
                    }
                    Box(Modifier.align(Alignment.TopEnd)) {
                        FileBrowserItemMenu(
                            file, readOnly, archivePath,
                            pickOptions != null, selectable, selected, menuExpanded,
                            { menuExpanded = it }, onAction
                        )
                    }
                }
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    maxLines = 2,
                    minLines = 2,
                    overflow = ellipsize.toComposeOverflow()
                )
                FileBrowserBadges(file)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileBrowserThumbnail(file, Modifier.size(44.dp), grid = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = foreground,
                        maxLines = 2,
                        overflow = ellipsize.toComposeOverflow()
                    )
                    if (!directory) {
                        val context = LocalContext.current
                        val size = file.attributes.fileSize.formatHumanReadable(context)
                        val modified = file.attributes.lastModifiedTime().toInstant()
                            .formatShort(context)
                        Text(
                            "$modified · $size",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) foreground
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    FileBrowserBadges(file)
                }
                if (selected || hasSelection) {
                    Checkbox(
                        checked = selected, onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
                FileBrowserItemMenu(
                    file, readOnly, archivePath,
                    pickOptions != null, selectable, selected, menuExpanded, { menuExpanded = it }, onAction
                )
            }
        }
    }
}

/**
 * Uncluttered 44dp list icons and restrained 80dp grid previews. Directory icons use the theme's
 * paired secondary-container colors, while Coil images are clipped and retain provider fetchers.
 * Never paint a square gray ImageView background behind every folder glyph.
 */
@Composable
private fun FileBrowserThumbnail(file: FileItem, modifier: Modifier = Modifier, grid: Boolean = false) {
    val directory = file.attributes.isDirectory
    val thumbnail = !directory && file.supportsThumbnail
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val shape = MaterialTheme.shapes.medium
    val background = if (directory) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        modifier = modifier.clip(shape),
        color = background,
        shape = shape
    ) {
        if (directory) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.file_browser_icon_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(if (grid) 40.dp else 26.dp)
                )
            }
        } else {
            val fallbackIcon = file.mimeType.iconRes
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(8, 8, 8, 8)
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
                    image.imageTintList = if (thumbnail) null
                        else ColorStateList.valueOf(iconTint)
                    image.scaleType = if (thumbnail) ImageView.ScaleType.CENTER_CROP
                        else ImageView.ScaleType.FIT_CENTER
                    val p = if (thumbnail) 0 else
                        (image.resources.displayMetrics.density * if (grid) 18 else 8).toInt()
                    if (image.paddingLeft != p) image.setPadding(p, p, p, p)
                },
                onRelease = { image -> image.dispose(); image.tag = null }
            )
        }
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
    selectable: Boolean,
    selected: Boolean,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onAction: (FileBrowserItemAction) -> Unit
) {
    Box {
        IconButton(onClick = { onExpanded(true) }) {
            Icon(
                painterResource(R.drawable.file_browser_icon_more),
                contentDescription = stringResource(R.string.file_browser_item_actions)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            if (selectable) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (selected)
                        R.string.file_browser_deselect else R.string.select)) },
                    onClick = { onExpanded(false); onAction(FileBrowserItemAction.SELECT) }
                )
            }
            @Composable
            fun item(id: Int, action: FileBrowserItemAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(id)) },
                    onClick = { onExpanded(false); onAction(action) }
                )
            }
            if (!file.attributes.isDirectory) {
                item(R.string.file_item_action_open_with, FileBrowserItemAction.OPEN_WITH)
            }
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
