@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import android.content.res.ColorStateList
import android.text.TextUtils
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.dispose
import coil.load
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.compose.components.VFilesListDefaults
import me.zhanghai.android.files.compose.components.singleLineBaseline

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

/** Hidden-file filtering and sorting happen off the main thread; null means "preparing". */
@Composable
internal fun rememberVisibleFiles(state: FileBrowserContentState): List<FileItem>? {
    val visible by key(state.pathKey, state.files, state.sort, state.showHidden, state.searching) {
        produceState<List<FileItem>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                val files = if (state.showHidden) state.files else state.files.filterNot { it.isHidden }
                if (state.searching) files else files.sortedWith(state.sort.createComparator())
            }
        }
    }
    return visible
}

/**
 * Lazy Compose rows and grid; stable path keys + content types, off-main sorting and no provider IO
 * during composition. SaveableStateProvider retains each path/mode scroll position across navigation.
 * The folder [header] is the first lazy item so it scrolls away with the files on small screens.
 */
@Composable
fun VFilesBrowserContent(
    state: FileBrowserContentState,
    onRefresh: () -> Unit,
    onItemAction: (FileItem, FileBrowserItemAction) -> Unit,
    onRangeSelect: (List<FileItem>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    nestedScrollConnection: NestedScrollConnection? = null,
    selectionEnabled: Boolean = true,
    header: @Composable () -> Unit = {}
) {
    val layoutDirection = LocalLayoutDirection.current
    // Do not briefly display the previous folder's items while an asynchronous sort runs.
    // A null result means preparing, never an empty directory eligible for user actions.
    val visibleResult = rememberVisibleFiles(state)
    val visible = visibleResult.orEmpty()
    val savedState = rememberSaveableStateHolder()
    val pullState = rememberPullToRefreshState()
    val refreshing = state.loading && state.files.isNotEmpty()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding())
            )
        }
    ) {
        val scrollModifier = if (nestedScrollConnection != null) {
            Modifier.nestedScroll(nestedScrollConnection)
        } else Modifier
        val listPadding = PaddingValues(
            start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
            end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        )
        val status: (@Composable () -> Unit)? = when {
            (visibleResult == null || state.loading) && visible.isEmpty() -> {
                { BrowserLoadingState() }
            }
            state.error != null && visible.isEmpty() -> {
                {
                    BrowserEmptyState(
                        icon = R.drawable.file_browser_icon_error,
                        title = stringResource(R.string.error),
                        detail = state.error,
                        actionLabel = stringResource(R.string.refresh),
                        onAction = onRefresh,
                        isError = true
                    )
                }
            }
            visible.isEmpty() -> {
                {
                    BrowserEmptyState(
                        icon = R.drawable.file_browser_icon_folder,
                        title = stringResource(
                            if (state.searching) R.string.file_browser_search_no_results
                            else R.string.file_list_empty
                        ),
                        detail = stringResource(R.string.file_browser_empty_description),
                        actionLabel = null,
                        onAction = null
                    )
                }
            }
            else -> null
        }
        val banner: @Composable () -> Unit = {
            if (state.error != null && visible.isNotEmpty()) {
                BrowserErrorBanner(state.error, onRefresh)
            }
            AnimatedVisibility(refreshing && !pullState.isAnimating) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        // Keep list and grid scroll states independently, for every visited provider path.
        savedState.SaveableStateProvider(state.pathKey + ":" + state.viewType.name) {
            if (state.viewType == FileViewType.LIST || status != null) {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    // Long names should not span an entire expanded window: bound the list.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().then(scrollModifier),
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        item(key = "header", contentType = "header") { header() }
                        item(key = "banner", contentType = "banner") { banner() }
                        if (status != null) {
                            item(key = "status", contentType = "status") { status() }
                        } else {
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
                                        rangeSelectOnKey(visible, index, key, shift, onRangeSelect)
                                    },
                                    selectionEnabled = selectionEnabled
                                )
                            }
                        }
                    }
                }
            } else {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().then(scrollModifier),
                    contentPadding = listPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) },
                        contentType = "header") { header() }
                    item(key = "banner", span = { GridItemSpan(maxLineSpan) },
                        contentType = "banner") { banner() }
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
                                rangeSelectOnKey(visible, index, key, shift, onRangeSelect)
                            },
                            selectionEnabled = selectionEnabled
                        )
                    }
                }
            }
        }
    }
}

private fun rangeSelectOnKey(
    visible: List<FileItem>,
    index: Int,
    key: Key,
    shift: Boolean,
    onRangeSelect: (List<FileItem>) -> Unit
): Boolean {
    val next = index + if (key == Key.DirectionDown) 1 else -1
    if (shift && next in visible.indices) {
        onRangeSelect(visible.subList(minOf(index, next), maxOf(index, next) + 1))
    }
    // Never consume: native focus traversal must still advance.
    return false
}

@Composable
internal fun BrowserLoadingState() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 96.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(Modifier.size(64.dp))
    }
}

/** Calm, centred empty and error states with an expressive container shape ([V03]). */
@Composable
internal fun BrowserEmptyState(
    icon: Int,
    title: String,
    detail: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val shape = MaterialShapes.Cookie9Sided.toShape()
        Surface(
            shape = shape,
            color = if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel) }
        }
    }
}

@Composable
private fun BrowserErrorBanner(error: String, onRefresh: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefresh, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.refresh))
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

/**
 * List rows are native expressive segmented list items ([C10]): one grouped surface with small
 * inner seams, a morphing selected shape and tonal selected colours. Selection swaps the leading
 * thumbnail for a check badge instead of permanently showing checkboxes on every file.
 * Grid tiles are media-first with the action menu over the preview.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileBrowserItem(
    file: FileItem,
    grid: Boolean,
    index: Int,
    count: Int,
    selected: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    ellipsize: TextUtils.TruncateAt,
    onAction: (FileBrowserItemAction) -> Unit,
    onKeyNavigation: (Key, Boolean) -> Boolean,
    selectionEnabled: Boolean = true
) {
    val directory = file.attributes.isDirectory
    val selectable = selectionEnabled && canSelectBrowserFile(file, pickOptions)
    val enabled = selectable || directory || !selectionEnabled
    val readOnly = file.path.fileSystem.isReadOnly
    val archivePath = file.path.isArchivePath
    var menuExpanded by remember(file.path) { mutableStateOf(false) }
    val keyModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
            onKeyNavigation(event.key, event.isShiftPressed)
        } else false
    }
    val onClick = {
        if (hasSelection && selectable) onAction(FileBrowserItemAction.SELECT)
        else onAction(FileBrowserItemAction.OPEN)
    }
    val onLongClick: (() -> Unit)? = if (selectable) {
        { onAction(FileBrowserItemAction.SELECT) }
    } else null
    val menu: @Composable () -> Unit = {
        FileBrowserItemMenu(
            file, readOnly, archivePath, pickOptions != null, selectable, selected,
            menuExpanded, { menuExpanded = it }, onAction
        )
    }
    if (grid) {
        FileBrowserGridTile(
            file, selected, hasSelection, enabled, ellipsize, onClick, onLongClick,
            keyModifier, menu
        )
        return
    }
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = VFilesListDefaults.segmentedColors(),
        modifier = Modifier.fillMaxWidth().then(keyModifier),
        enabled = enabled,
        onLongClick = onLongClick,
        onLongClickLabel = if (selectable) stringResource(R.string.select) else null,
        leadingContent = {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                FileBrowserThumbnail(file, Modifier.size(44.dp), grid = false)
                AnimatedVisibility(selected) { SelectedBadge(Modifier.size(44.dp)) }
            }
        },
        trailingContent = { menu() },
        supportingContent = if (!directory || file.hasBadges) {
            { FileBrowserSupportingText(file, directory) }
        } else null,
        content = {
            Text(
                file.name,
                maxLines = 2,
                overflow = ellipsize.toComposeOverflow()
            )
        }
    )
}

@Composable
private fun FileBrowserSupportingText(file: FileItem, directory: Boolean) {
    Row(
        modifier = Modifier.singleLineBaseline(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!directory) {
            val context = LocalContext.current
            val size = file.attributes.fileSize.formatHumanReadable(context)
            val modified = file.attributes.lastModifiedTime().toInstant().formatShort(context)
            Text(
                "$modified · $size",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        FileBrowserBadges(file)
    }
}

private val FileItem.hasBadges: Boolean
    get() = attributesNoFollowLinks.isSymbolicLink || attributesNoFollowLinks.isEncrypted() ||
        isArchiveFile || path.isArchivePath || path.fileSystem.isReadOnly

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.check_icon_control_normal_24dp),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserGridTile(
    file: FileItem,
    selected: Boolean,
    hasSelection: Boolean,
    enabled: Boolean,
    ellipsize: TextUtils.TruncateAt,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    keyModifier: Modifier,
    menu: @Composable () -> Unit
) {
    val directory = file.attributes.isDirectory
    // Selection morphs the tile corner: a quiet, physical confirmation of the state change.
    val corner by animateDpAsState(
        if (selected) 32.dp else 20.dp,
        MaterialTheme.motionScheme.fastSpatialSpec()
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier.fillMaxWidth().clip(shape).semantics { this.selected = selected }
            .then(keyModifier)
            .combinedClickable(
                enabled = enabled,
                onClickLabel = if (hasSelection) stringResource(
                    if (selected) R.string.file_browser_deselect else R.string.select
                ) else null,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.select) else null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.25f),
                contentAlignment = Alignment.Center
            ) {
                FileBrowserThumbnail(
                    file = file,
                    grid = true,
                    modifier = if (directory || !file.supportsThumbnail) Modifier.size(72.dp)
                        else Modifier.fillMaxSize()
                )
                if (selected) {
                    SelectedBadge(Modifier.align(Alignment.TopStart).padding(4.dp).size(28.dp))
                }
                Box(Modifier.align(Alignment.TopEnd)) { menu() }
            }
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = ellipsize.toComposeOverflow(),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box(Modifier.padding(horizontal = 4.dp, vertical = 0.dp)) { FileBrowserBadges(file) }
        }
    }
}

/**
 * 44dp list icons and restrained grid previews. Directory icons use the paired secondary
 * container colours, while Coil images are clipped and retain the app's provider fetchers.
 */
@Composable
private fun FileBrowserThumbnail(file: FileItem, modifier: Modifier = Modifier, grid: Boolean = false) {
    val directory = file.attributes.isDirectory
    val thumbnail = !directory && file.supportsThumbnail
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val shape = if (grid) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val background = if (directory) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(modifier = modifier.clip(shape), color = background, shape = shape) {
        if (directory) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.file_browser_icon_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(if (grid) 36.dp else 24.dp)
                )
            }
        } else if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(file.mimeType.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (grid) 36.dp else 24.dp)
                )
            }
        } else {
            val fallbackIcon = file.mimeType.iconRes
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                        (image.resources.displayMetrics.density * if (grid) 18 else 10).toInt()
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
                    stringResource(R.string.file_browser_badge_symbolic_link)
                    else stringResource(R.string.file_browser_badge_encrypted),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (file.isArchiveFile || file.path.isArchivePath) {
            BrowserBadgeLabel(stringResource(R.string.file_browser_badge_archive))
        }
        if (file.path.fileSystem.isReadOnly) {
            BrowserBadgeLabel(stringResource(R.string.file_browser_badge_read_only))
        }
    }
}

@Composable
private fun BrowserBadgeLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** Context menu actions mirror res/menu/file_item.xml, grouped by intent ([C17]). */
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
        IconButton(onClick = { onExpanded(true) }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(R.drawable.file_browser_icon_more),
                contentDescription = stringResource(R.string.file_browser_item_actions)
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            val directory = file.attributes.isDirectory
            val groups = buildList {
                add(buildList {
                    if (selectable) add(
                        (if (selected) R.string.file_browser_deselect else R.string.select) to
                            FileBrowserItemAction.SELECT
                    )
                    if (!directory) add(R.string.file_item_action_open_with to FileBrowserItemAction.OPEN_WITH)
                    if (!picker && !readOnly && !archivePath && !directory) {
                        add(R.string.edit to FileBrowserItemAction.EDIT_WITH)
                    }
                })
                if (!picker) {
                    add(buildList {
                        if (!readOnly) add(R.string.cut to FileBrowserItemAction.CUT)
                        add((if (archivePath) R.string.file_item_action_extract else R.string.copy) to
                            FileBrowserItemAction.COPY)
                        if (!readOnly) {
                            add(R.string.rename to FileBrowserItemAction.RENAME)
                            add(R.string.delete to FileBrowserItemAction.DELETE)
                        }
                    })
                    add(buildList {
                        if (file.isArchiveFile) add(R.string.file_item_action_extract to FileBrowserItemAction.EXTRACT)
                        if (!archivePath) add(R.string.file_item_action_archive to FileBrowserItemAction.ARCHIVE)
                        add(R.string.share to FileBrowserItemAction.SHARE)
                        add(R.string.file_item_action_copy_path to FileBrowserItemAction.COPY_PATH)
                    })
                    add(buildList {
                        if (directory) add(R.string.file_item_action_add_bookmark to FileBrowserItemAction.BOOKMARK)
                        add(R.string.file_item_action_create_shortcut to FileBrowserItemAction.SHORTCUT)
                        add(R.string.file_item_action_properties to FileBrowserItemAction.PROPERTIES)
                    })
                }
            }.filter { it.isNotEmpty() }
            groups.forEachIndexed { groupIndex, group ->
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                    group.forEachIndexed { index, (title, action) ->
                        val destructive = action == FileBrowserItemAction.DELETE
                        DropdownMenuItem(
                            onClick = { onExpanded(false); onAction(action) },
                            text = {
                                Text(
                                    stringResource(title),
                                    color = if (destructive) MaterialTheme.colorScheme.error
                                        else androidx.compose.ui.graphics.Color.Unspecified
                                )
                            },
                            shape = MenuDefaults.itemShape(index, group.size).shape
                        )
                    }
                }
                if (groupIndex != groups.lastIndex) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                }
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
