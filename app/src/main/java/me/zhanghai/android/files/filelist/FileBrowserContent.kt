@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import android.text.TextUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesListDefaults
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
    val nameEllipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
    /** Incremented by the host for every user-requested refresh. */
    val refreshToken: Int = 0,
    val dragToSelect: Boolean = true,
    /** Tapping a row's icon selects it instead of opening it. */
    val iconTapSelects: Boolean = false
)

enum class FileBrowserItemAction {
    OPEN, SELECT, OPEN_WITH, EDIT_WITH, CUT, COPY, DELETE, RENAME,
    EXTRACT, ARCHIVE, SHARE, COPY_PATH, BOOKMARK, SHORTCUT, PROPERTIES
}

/**
 * The files actually on screen. While a folder is still loading with nothing to show, this keeps
 * the previous result for [LOADING_INDICATOR_DELAY_MILLIS], so fast loads swap content directly
 * instead of flashing a blank frame or a spinner.
 */
@Immutable
class DisplayedFiles internal constructor(
    val pathKey: String,
    val files: List<FileItem>,
    val searching: Boolean,
    val loading: Boolean,
    val error: String?
)

internal const val LOADING_INDICATOR_DELAY_MILLIS = 300L
private const val REFRESH_INDICATOR_MIN_MILLIS = 1000L

/** Hidden-file filtering and sorting happen off the main thread; null means nothing shown yet. */
@Composable
fun rememberDisplayedFiles(
    state: FileBrowserContentState,
    loadingDelayMillis: Long = LOADING_INDICATOR_DELAY_MILLIS
): DisplayedFiles? {
    var displayed by remember { mutableStateOf<DisplayedFiles?>(null) }
    val waitingForFiles = state.loading && state.files.isEmpty()
    LaunchedEffect(
        state.pathKey, state.files, state.sort, state.showHidden, state.searching,
        waitingForFiles, state.error
    ) {
        val files = withContext(Dispatchers.Default) {
            val files = if (state.showHidden) state.files else state.files.filterNot { it.isHidden }
            if (state.searching) files else files.sortedWith(state.sort.createComparator())
        }
        if (waitingForFiles) delay(loadingDelayMillis)
        displayed = DisplayedFiles(state.pathKey, files, state.searching, state.loading, state.error)
    }
    return displayed
}

@Immutable
private data class BrowserPage(val displayed: DisplayedFiles?, val viewType: FileViewType) {
    val key: String get() = (displayed?.pathKey ?: "") + ":" + viewType.name
}

/**
 * Lazy Compose rows and grid; stable path keys + content types, off-main sorting and no provider IO
 * during composition. SaveableStateProvider retains each path/mode scroll position across navigation.
 * The folder header lives outside the list; [scrollToTopToken] changes scroll the page to its top.
 * [backProgress] is the predictive back gesture towards the parent folder: the page on screen
 * follows it, then the parent slides in when the gesture commits.
 */
@Composable
fun VFilesBrowserContent(
    state: FileBrowserContentState,
    onRefresh: () -> Unit,
    onItemAction: (FileItem, FileBrowserItemAction) -> Unit,
    onRangeSelect: (List<FileItem>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    displayed: DisplayedFiles? = rememberDisplayedFiles(state),
    onSetSelection: (List<FileItem>, Boolean) -> Unit = { _, _ -> },
    nestedScrollConnection: NestedScrollConnection? = null,
    selectionEnabled: Boolean = true,
    scrollToTopToken: Int = 0,
    backProgress: () -> Float = { 0f },
    picker: Boolean = state.pickOptions != null
) {
    val savedState = rememberSaveableStateHolder()
    var menuFile by remember { mutableStateOf<FileItem?>(null) }
    // The page that was on screen when a back gesture began keeps following it while leaving.
    val backKey = remember { mutableStateOf<String?>(null) }
    val currentKey by rememberUpdatedState(BrowserPage(displayed, state.viewType).key)
    val currentBackProgress by rememberUpdatedState(backProgress)
    LaunchedEffect(Unit) {
        snapshotFlow { currentBackProgress() > 0f }.collect { active ->
            if (active) backKey.value = currentKey
        }
    }
    val pullState = rememberPullToRefreshState()
    val refreshing = rememberRefreshIndicator(state)
    val motion = MaterialTheme.motionScheme
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
        AnimatedContent(
            targetState = BrowserPage(displayed, state.viewType),
            contentKey = { it.key },
            transitionSpec = { folderTransition(motion) },
            label = "browserFolder"
        ) { page ->
            val pageModifier = Modifier.graphicsLayer {
                if (page.key != backKey.value) return@graphicsLayer
                val progress = backProgress()
                translationX = progress * 32.dp.toPx()
                val scale = 1f - 0.06f * progress
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.3f * progress
            }
            // Keep list and grid scroll states independently, for every visited provider path.
            savedState.SaveableStateProvider(page.key) {
              Box(pageModifier) {
                BrowserPageContent(
                    page = page,
                    state = state,
                    refreshing = refreshing,
                    onRefresh = onRefresh,
                    onItemAction = onItemAction,
                    onRangeSelect = onRangeSelect,
                    onSetSelection = onSetSelection,
                    contentPadding = contentPadding,
                    nestedScrollConnection = nestedScrollConnection,
                    selectionEnabled = selectionEnabled,
                    scrollToTopToken = scrollToTopToken,
                    onOpenMenu = { menuFile = it }
                )
              }
            }
        }
    }
    menuFile?.let { file ->
        val selectable = selectionEnabled && canSelectBrowserFile(file, state.pickOptions)
        FileBrowserItemSheet(
            file = file,
            selected = file.path in state.selectedPaths,
            selectable = selectable,
            picker = picker,
            onAction = { onItemAction(file, it) },
            onDismiss = { menuFile = null }
        )
    }
}

/**
 * The pull indicator stays for at least [REFRESH_INDICATOR_MIN_MILLIS] so a fast reload still
 * reads as a completed refresh; new files appear as soon as they arrive regardless.
 */
@Composable
private fun rememberRefreshIndicator(state: FileBrowserContentState): Boolean {
    var showing by remember { mutableStateOf(false) }
    val initialToken = remember { state.refreshToken }
    val loading by rememberUpdatedState(state.loading)
    LaunchedEffect(state.refreshToken) {
        if (state.refreshToken == initialToken) return@LaunchedEffect
        showing = true
        delay(REFRESH_INDICATOR_MIN_MILLIS)
        snapshotFlow { loading }.first { !it }
        showing = false
    }
    return showing
}

/** Shared-axis folder motion: deeper folders enter from the end, parents from the start. */
private fun AnimatedContentTransitionScope<BrowserPage>.folderTransition(
    motion: MotionScheme
): ContentTransform {
    val from = initialState.displayed?.pathKey
    val to = targetState.displayed?.pathKey
    val direction = when {
        from == null || to == null || from == to -> 0
        to.startsWith(from) -> 1
        from.startsWith(to) -> -1
        else -> 0
    }
    // Shared axis X: a short slide with a fade-through, the outgoing page leaving quickly so
    // the two lists never read as overlapping. Sideways moves (e.g. a bookmark) zoom softly.
    val enter = fadeIn(motion.defaultEffectsSpec(), initialAlpha = 0f) +
        slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { it / 10 * direction } +
        scaleIn(motion.defaultSpatialSpec(), initialScale = if (direction == 0) 0.94f else 1f)
    val exit = fadeOut(motion.fastEffectsSpec()) +
        slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { -it / 10 * direction } +
        scaleOut(motion.fastSpatialSpec(), targetScale = if (direction == 0) 1.03f else 1f)
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

@Composable
private fun BrowserPageContent(
    page: BrowserPage,
    state: FileBrowserContentState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onItemAction: (FileItem, FileBrowserItemAction) -> Unit,
    onRangeSelect: (List<FileItem>) -> Unit,
    onSetSelection: (List<FileItem>, Boolean) -> Unit,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection?,
    selectionEnabled: Boolean,
    scrollToTopToken: Int,
    onOpenMenu: (FileItem) -> Unit
) {
    val initialScrollToken = remember { scrollToTopToken }
    val layoutDirection = LocalLayoutDirection.current
    val displayed = page.displayed
    val visible = displayed?.files.orEmpty()
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
        displayed == null -> { {} }
        visible.isNotEmpty() -> null
        displayed.loading && !refreshing -> { { BrowserLoadingState() } }
        displayed.error != null -> {
            {
                BrowserEmptyState(
                    icon = R.drawable.file_browser_icon_error,
                    title = stringResource(R.string.error),
                    detail = displayed.error,
                    actionLabel = stringResource(R.string.refresh),
                    onAction = onRefresh,
                    isError = true
                )
            }
        }
        else -> {
            {
                BrowserEmptyState(
                    icon = R.drawable.file_browser_icon_folder,
                    title = stringResource(
                        if (displayed.searching) R.string.file_browser_search_no_results
                        else R.string.file_list_empty
                    ),
                    detail = stringResource(R.string.file_browser_empty_description),
                    actionLabel = null,
                    onAction = null
                )
            }
        }
    }
    val banner: @Composable () -> Unit = {
        val error = displayed?.error
        if (error != null && visible.isNotEmpty()) BrowserErrorBanner(error, onRefresh)
    }
    val pick = state.pickOptions
    val dragEnabled = selectionEnabled && state.dragToSelect && status == null &&
        (pick == null || pick.allowMultiple)
    val itemContent: @Composable (Int, FileItem, Boolean) -> Unit = { index, file, grid ->
        FileBrowserItem(
            file = file,
            grid = grid,
            index = index,
            count = visible.size,
            selected = file.path in state.selectedPaths,
            hasSelection = state.selectedPaths.isNotEmpty(),
            pickOptions = pick,
            ellipsize = state.nameEllipsize,
            onAction = { onItemAction(file, it) },
            onKeyNavigation = { key, shift ->
                rangeSelectOnKey(visible, index, key, shift, onRangeSelect)
            },
            selectionEnabled = selectionEnabled,
            longPressSelects = !dragEnabled,
            iconTapSelects = state.iconTapSelects,
            onOpenMenu = { onOpenMenu(file) }
        )
    }
    if (state.viewType == FileViewType.LIST || status != null) {
        val listState = rememberLazyListState()
        LaunchedEffect(scrollToTopToken) {
            if (scrollToTopToken != initialScrollToken) listState.animateScrollToItem(0)
        }
        val dragModifier = dragToSelectModifier(
            enabled = dragEnabled,
            files = visible,
            selectedPaths = state.selectedPaths,
            pickOptions = pick,
            contentPadding = listPadding,
            hitTest = { listState.fileIndexAt(it, visible.size) },
            scrollBy = { listState.scrollBy(it) },
            onSetSelection = onSetSelection
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Long names should not span an entire expanded window: bound the list.
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().then(scrollModifier)
                    .then(dragModifier),
                contentPadding = listPadding,
                userScrollEnabled = !refreshing,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                item(key = "banner", contentType = "banner") { banner() }
                if (status != null) {
                    item(key = "status", contentType = "status") { status() }
                } else {
                    itemsIndexed(visible, key = { _, file -> file.path.toString() },
                        contentType = { _, file ->
                            if (file.attributes.isDirectory) "directory" else "file"
                        }) { index, file -> itemContent(index, file, false) }
                }
            }
        }
    } else {
        val gridState = rememberLazyGridState()
        LaunchedEffect(scrollToTopToken) {
            if (scrollToTopToken != initialScrollToken) gridState.animateScrollToItem(0)
        }
        val dragModifier = dragToSelectModifier(
            enabled = dragEnabled,
            files = visible,
            selectedPaths = state.selectedPaths,
            pickOptions = pick,
            contentPadding = listPadding,
            hitTest = { gridState.fileIndexAt(it, visible.size) },
            scrollBy = { gridState.scrollBy(it) },
            onSetSelection = onSetSelection
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().then(scrollModifier).then(dragModifier),
            contentPadding = listPadding,
            userScrollEnabled = !refreshing,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "banner", span = { GridItemSpan(maxLineSpan) },
                contentType = "banner") { banner() }
            gridItemsIndexed(visible, key = { _, file -> file.path.toString() },
                contentType = { _, file ->
                    if (file.attributes.isDirectory) "directory" else "file"
                }) { index, file -> itemContent(index, file, true) }
        }
    }
}

/** The error banner precedes the files in both lazy layouts. */
private const val FILE_ITEM_OFFSET = 1

internal fun LazyListState.fileIndexAt(position: Offset, count: Int): Int? {
    val info = layoutInfo
    val y = position.y.toInt() + info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
        ?: return null
    return (item.index - FILE_ITEM_OFFSET).takeIf { it in 0 until count }
}

internal fun LazyGridState.fileIndexAt(position: Offset, count: Int): Int? {
    val info = layoutInfo
    val y = position.y.toInt() + info.viewportStartOffset
    val x = position.x.toInt()
    val item = info.visibleItemsInfo.firstOrNull {
        y >= it.offset.y && y < it.offset.y + it.size.height &&
            x >= it.offset.x && x < it.offset.x + it.size.width
    } ?: return null
    return (item.index - FILE_ITEM_OFFSET).takeIf { it in 0 until count }
}

/**
 * Long press, then drag across rows to select the range between the anchor and the finger.
 * Rows that leave the range return to their state from before the drag. Near the top or bottom
 * edge the list scrolls by itself. Until the long press fires nothing is consumed, so taps,
 * flings and row clicks behave normally.
 */
@Composable
private fun dragToSelectModifier(
    enabled: Boolean,
    files: List<FileItem>,
    selectedPaths: Set<Path>,
    pickOptions: PickOptions?,
    contentPadding: PaddingValues,
    hitTest: (Offset) -> Int?,
    scrollBy: suspend (Float) -> Float,
    onSetSelection: (List<FileItem>, Boolean) -> Unit
): Modifier {
    if (!enabled) return Modifier
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentSelected by rememberUpdatedState(selectedPaths)
    val currentHitTest by rememberUpdatedState(hitTest)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    val currentOnSetSelection by rememberUpdatedState(onSetSelection)
    val currentPick by rememberUpdatedState(pickOptions)
    val topEdge = with(density) { contentPadding.calculateTopPadding().toPx() + 48.dp.toPx() }
    val bottomInset = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val zone = with(density) { 72.dp.toPx() }
    val maxStep = with(density) { 14.dp.toPx() }
    return Modifier.pointerInput(files) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val anchor = currentHitTest(down.position) ?: return@awaitEachGesture
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) return@withTimeoutOrNull false
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull false
                        if (!change.pressed || change.isConsumed ||
                            (change.position - down.position).getDistance() >
                            viewConfiguration.touchSlop) {
                            return@withTimeoutOrNull false
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                } == null
                if (!longPressed) return@awaitEachGesture
                val anchorFile = files.getOrNull(anchor) ?: return@awaitEachGesture
                if (!canSelectBrowserFile(anchorFile, currentPick)) return@awaitEachGesture
                val initial = currentSelected
                val select = anchorFile.path !in initial
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSetSelection(listOf(anchorFile), select)
                var range = anchor..anchor
                var pointer = down.position
                fun extendTo(target: Int) {
                    val next = minOf(anchor, target)..maxOf(anchor, target)
                    if (next == range) return
                    val added = next.filter { it !in range }.map { files[it] }
                        .filter { canSelectBrowserFile(it, currentPick) }
                    val restored = range.filter { it !in next }.map { files[it] }
                        .filter { (it.path in initial) != select }
                    range = next
                    if (added.isNotEmpty()) currentOnSetSelection(added, select)
                    if (restored.isNotEmpty()) currentOnSetSelection(restored, !select)
                }
                fun edgeStep(): Float {
                    val bottomEdge = size.height - bottomInset
                    return when {
                        pointer.y < topEdge -> -((topEdge - pointer.y) / zone).coerceAtMost(1f)
                        pointer.y > bottomEdge - zone ->
                            ((pointer.y - (bottomEdge - zone)) / zone).coerceAtMost(1f)
                        else -> 0f
                    } * maxStep
                }
                // Frames are only requested while the finger rests in an edge zone.
                var autoScroll: Job? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // Rows must not click on release and the list must not scroll.
                        event.changes.forEach { it.consume() }
                        if (change == null || !change.pressed) break
                        pointer = change.position
                        currentHitTest(pointer)?.let(::extendTo)
                        if (edgeStep() != 0f && autoScroll?.isActive != true) {
                            autoScroll = launch {
                                while (isActive) {
                                    val step = edgeStep()
                                    if (step == 0f) break
                                    withFrameNanos { }
                                    if (currentScrollBy(step) == 0f) break
                                    currentHitTest(pointer)?.let(::extendTo)
                                }
                            }
                        }
                    }
                } finally {
                    autoScroll?.cancel()
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
 * inner seams, a morphing selected shape and tonal selected colours. Selection morphs the leading
 * thumbnail into a check badge instead of permanently showing checkboxes on every file.
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
    selectionEnabled: Boolean = true,
    longPressSelects: Boolean = true,
    iconTapSelects: Boolean = false,
    onOpenMenu: () -> Unit = {}
) {
    val directory = file.attributes.isDirectory
    val selectable = selectionEnabled && canSelectBrowserFile(file, pickOptions)
    val enabled = selectable || directory || !selectionEnabled
    val readOnly = file.path.fileSystem.isReadOnly
    val archivePath = file.path.isArchivePath
    val selectLabel = stringResource(R.string.select)
    val keyModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
            onKeyNavigation(event.key, event.isShiftPressed)
        } else false
    }.then(
        // With drag to select the list owns the long press gesture; keep the semantic action.
        if (selectable && !longPressSelects) {
            Modifier.semantics {
                onLongClick(selectLabel) { onAction(FileBrowserItemAction.SELECT); true }
            }
        } else Modifier
    )
    val onClick = {
        if (hasSelection && selectable) onAction(FileBrowserItemAction.SELECT)
        else onAction(FileBrowserItemAction.OPEN)
    }
    val onLongClick: (() -> Unit)? = if (selectable && longPressSelects) {
        { onAction(FileBrowserItemAction.SELECT) }
    } else null
    val menu: @Composable () -> Unit = {
        IconButton(onClick = onOpenMenu, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(R.drawable.file_browser_icon_more),
                contentDescription = stringResource(R.string.file_browser_item_actions)
            )
        }
    }
    // With the preference on, the icon is a selection toggle of its own.
    val iconModifier = if (iconTapSelects && selectable) {
        Modifier.clip(CircleShape).combinedClickable(
            onClickLabel = stringResource(
                if (selected) R.string.file_browser_deselect else R.string.select
            ),
            onClick = { onAction(FileBrowserItemAction.SELECT) }
        )
    } else Modifier
    val supporting = rememberSupportingText(file)
    if (grid) {
        FileBrowserGridTile(
            file, supporting, selected, hasSelection, enabled, ellipsize, onClick, onLongClick,
            keyModifier, menu, iconModifier
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
        onLongClickLabel = if (onLongClick != null) selectLabel else null,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Box(iconModifier) { FileBrowserLeading(file, selected) } },
        trailingContent = { menu() },
        // ListItem reads the supporting slot's baselines while measuring. Keep it a single leaf
        // Text: a Row here makes Compose 1.12 lay out its children from inside a lazy item's
        // measure pass, which crashes with "LayoutNode not found in RectList" while scrolling.
        supportingContent = supporting?.let { text ->
            { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        content = {
            Text(
                file.name,
                maxLines = 2,
                overflow = ellipsize.toComposeOverflow()
            )
        }
    )
}

/** "Modified · size" for files, plus the archive role; folders keep a single quiet line. */
@Composable
private fun rememberSupportingText(file: FileItem): String? {
    if (file.attributes.isDirectory) return null
    val context = LocalContext.current
    return remember(file.attributes, context) { fileSupportingText(file, context) }
}

internal fun fileSupportingText(file: FileItem, context: android.content.Context): String? {
    if (file.attributes.isDirectory) return null
    val size = file.attributes.fileSize.formatHumanReadable(context)
    val modified = file.attributes.lastModifiedTime().toInstant().formatShort(context)
    return buildString {
        append(modified).append(" · ").append(size)
        if (file.isArchiveFile) {
            append(" · ").append(context.getString(R.string.file_browser_badge_archive))
        }
    }
}

private val thumbnailCorner = 12.dp
private val leadingSize = 44.dp

/**
 * Selection morphs the whole leading tile: its corners round into a circle while the thumbnail
 * fades and shrinks away under the primary check, so no icon, image or container corner shows
 * beneath the check once the motion settles.
 */
@Composable
private fun FileBrowserLeading(file: FileItem, selected: Boolean) {
    val motion = MaterialTheme.motionScheme
    val transition = updateTransition(selected, label = "fileSelection")
    val progress by transition.animateFloat(transitionSpec = { motion.fastSpatialSpec() }) {
        if (it) 1f else 0f
    }
    val corner by transition.animateDp(transitionSpec = { motion.fastSpatialSpec() }) {
        if (it) leadingSize / 2 else thumbnailCorner
    }
    val checkComposed = transition.currentState || transition.targetState
    Box(Modifier.size(leadingSize)) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                shape = RoundedCornerShape(corner)
                clip = true
            },
            contentAlignment = Alignment.Center
        ) {
            FileBrowserThumbnail(
                file,
                Modifier.matchParentSize().graphicsLayer {
                    val p = progress.coerceIn(0f, 1f)
                    alpha = 1f - p
                    scaleX = 1f - 0.4f * p
                    scaleY = scaleX
                },
                grid = false,
                shape = null
            )
            if (checkComposed) {
                val primary = MaterialTheme.colorScheme.primary
                Box(
                    Modifier.matchParentSize()
                        .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                        .background(primary)
                )
                Icon(
                    painterResource(R.drawable.check_icon_control_normal_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        val p = progress.coerceAtLeast(0f)
                        alpha = p.coerceAtMost(1f)
                        scaleX = 0.4f + 0.6f * p
                        scaleY = scaleX
                    }
                )
            }
        }
        FileBrowserLinkBadge(
            file,
            Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp)
                .graphicsLayer { alpha = 1f - progress.coerceIn(0f, 1f) }
        )
    }
}

/** Symbolic link and encryption state ride on the thumbnail corner instead of the text line. */
@Composable
private fun FileBrowserLinkBadge(file: FileItem, modifier: Modifier = Modifier) {
    val attributes = file.attributesNoFollowLinks
    val (icon, label) = when {
        attributes.isSymbolicLink -> (
            if (file.isSymbolicLinkBroken) R.drawable.error_badge_icon_18dp
            else R.drawable.symbolic_link_badge_icon_18dp
        ) to R.string.file_browser_badge_symbolic_link
        attributes.isEncrypted() ->
            R.drawable.encrypted_badge_icon_18dp to R.string.file_browser_badge_encrypted
        else -> return
    }
    Surface(
        modifier = modifier.size(20.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = stringResource(label),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserGridTile(
    file: FileItem,
    supporting: String?,
    selected: Boolean,
    hasSelection: Boolean,
    enabled: Boolean,
    ellipsize: TextUtils.TruncateAt,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    keyModifier: Modifier,
    menu: @Composable () -> Unit,
    iconModifier: Modifier = Modifier
) {
    val directory = file.attributes.isDirectory
    val motion = MaterialTheme.motionScheme
    // Selection morphs the tile corner: a quiet, physical confirmation of the state change.
    val corner by animateDpAsState(if (selected) 32.dp else 20.dp, motion.fastSpatialSpec())
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
                Box(
                    (if (directory || !file.supportsThumbnail) Modifier.size(72.dp)
                    else Modifier.fillMaxSize()).then(iconModifier)
                ) {
                    FileBrowserThumbnail(file = file, grid = true, modifier = Modifier.matchParentSize())
                    FileBrowserLinkBadge(file, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
                GridSelectedBadge(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
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
            Text(
                supporting.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun GridSelectedBadge(selected: Boolean, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = selected,
        modifier = modifier,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.check_icon_control_normal_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 44dp list icons and restrained grid previews. Directory icons use the paired secondary
 * container colours; Coil Compose images share the app's singleton loader, memory cache and
 * provider fetchers, without one Android View per row.
 */
@Composable
private fun FileBrowserThumbnail(
    file: FileItem,
    modifier: Modifier = Modifier,
    grid: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape? =
        if (grid) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
) {
    val directory = file.attributes.isDirectory
    val thumbnail = !directory && file.supportsThumbnail && !LocalInspectionMode.current
    val background = if (directory) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val clipped = if (shape != null) modifier.clip(shape) else modifier
    Box(clipped.background(background), contentAlignment = Alignment.Center) {
        var loaded by remember(file.path, file.attributes) { mutableStateOf(false) }
        if (!loaded) {
            Icon(
                painter = painterResource(
                    if (directory) R.drawable.file_browser_icon_folder else file.mimeType.iconRes
                ),
                contentDescription = null,
                tint = if (directory) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (grid) 36.dp else 24.dp)
            )
        }
        if (thumbnail) {
            val context = LocalContext.current
            val request = remember(file.path, file.attributes, context) {
                ImageRequest.Builder(context)
                    .data(file.path to file.attributes)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onSuccess = { loaded = true },
                onError = { loaded = false }
            )
        }
    }
}

private fun TextUtils.TruncateAt.toComposeOverflow(): TextOverflow = when (this) {
    TextUtils.TruncateAt.START -> TextOverflow.StartEllipsis
    TextUtils.TruncateAt.MIDDLE -> TextOverflow.MiddleEllipsis
    TextUtils.TruncateAt.MARQUEE -> TextOverflow.Clip
    else -> TextOverflow.Ellipsis
}
