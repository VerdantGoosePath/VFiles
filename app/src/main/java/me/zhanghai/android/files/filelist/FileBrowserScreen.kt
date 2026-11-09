@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.navigation.VFilesNavigationRailContent
import me.zhanghai.android.files.navigation.VFilesNavigationRailHeader

/** Storage destinations for the rail; NavigationItem objects keep their own actions. */
data class FileBrowserNavigationState(
    val items: List<NavigationItem?> = emptyList(),
    val currentPath: Path? = null,
    val revision: Int = 0,
    val modalOpen: Boolean = false,
    val persistentExpanded: Boolean = true
)

/**
 * Every intent the screen can raise. The fragment implements it on top of FileListViewModel,
 * so permission, read-only and picker policy stay in one place.
 */
interface FileBrowserScreenActions {
    fun onBreadcrumb(id: String)
    fun onViewType(viewType: FileViewType)
    fun onSortBy(by: FileSortOptions.By)
    fun onSortOrder(order: FileSortOptions.Order)
    fun onDirectoriesFirst(directoriesFirst: Boolean)
    fun onPathSpecificSort(pathSpecific: Boolean)
    fun onShowHiddenFiles(show: Boolean)
    fun onSearchActiveChange(active: Boolean)
    fun onSearchQueryChange(query: String)
    fun onSearchSubmit(query: String)
    fun onRefresh()
    fun onItemAction(file: FileItem, action: FileBrowserItemAction)
    fun onRangeSelect(files: List<FileItem>)
    fun onSetSelection(files: List<FileItem>, selected: Boolean)
    fun onSelection(command: FileBrowserSelectionCommand)
    fun onFolderAction(action: FileBrowserFolderAction)
    fun onCreateMenuExpandedChange(expanded: Boolean)
    fun onCreateFile()
    fun onCreateDirectory()
    fun onCancelJob(id: Int)
    fun onNavigationOpenChange(open: Boolean)
    fun onTogglePersistentNavigation()
    /** External picker: return the folder on screen (OPEN_DOCUMENT_TREE). */
    fun onPickCurrentDirectory() {}
    /** External picker: save under [fileName] in the folder on screen (CREATE_DOCUMENT). */
    fun onPickCreateFile(fileName: String) {}
    fun onPickCancel() {}
}

/** Typing pauses this long before a recursive provider search starts. */
private const val SEARCH_DEBOUNCE_MILLIS = 350L

/**
 * The whole browser as one expressive surface: navigation rail, flexible app bar with search,
 * files, floating toolbars and the FAB menu share one theme, one inset model and one motion
 * scheme. External picker sessions use the same surface with a picker bar at the bottom.
 */
@Composable
fun FileBrowserScreen(
    chrome: FileBrowserChromeState,
    content: FileBrowserContentState,
    jobs: List<FileJobProgress>,
    navigation: FileBrowserNavigationState,
    navigationListener: NavigationItem.Listener,
    createMenuExpanded: Boolean,
    actions: FileBrowserScreenActions
) {
    SystemBarAppearance()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Window width, not device type: a phone in landscape or a resized window also gets
        // the persistent rail ([I03]).
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                val railState = rememberWideNavigationRailState(
                    if (navigation.persistentExpanded) WideNavigationRailValue.Expanded
                    else WideNavigationRailValue.Collapsed
                )
                LaunchedEffect(navigation.persistentExpanded) {
                    if (navigation.persistentExpanded) railState.expand() else railState.collapse()
                }
                val railExpanded = railState.targetValue == WideNavigationRailValue.Expanded
                WideNavigationRail(
                    state = railState,
                    header = {
                        VFilesNavigationRailHeader(
                            expanded = railExpanded,
                            onToggle = actions::onTogglePersistentNavigation
                        )
                    }
                ) {
                    VFilesNavigationRailContent(
                        items = navigation.items,
                        currentPath = navigation.currentPath,
                        revision = navigation.revision,
                        expanded = railExpanded,
                        listener = navigationListener,
                        onAddBookmark = { actions.onFolderAction(FileBrowserFolderAction.ADD_BOOKMARK) }
                    )
                }
                BrowserScaffold(
                    chrome, content, jobs, createMenuExpanded, actions,
                    showNavigationIcon = false,
                    navigationOpen = false,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            val panelWidth = minOf(maxWidth - 56.dp, 360.dp)
            ModalBrowserRail(
                navigation = navigation,
                navigationListener = navigationListener,
                actions = actions,
                swipeToOpen = chrome.swipeToOpenNavigation && !chrome.searchExpanded,
                panelWidth = panelWidth
            ) {
                BrowserScaffold(
                    chrome, content, jobs, createMenuExpanded, actions,
                    showNavigationIcon = true,
                    navigationOpen = navigation.modalOpen,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Modal rail for compact windows. It is drawn in the browser's own window, so a swipe that
 * starts anywhere over the files can pull it out under the finger, and a predictive back
 * gesture slides it away in step with the gesture. Its open state stays owned by the host.
 */
@Composable
private fun ModalBrowserRail(
    navigation: FileBrowserNavigationState,
    navigationListener: NavigationItem.Listener,
    actions: FileBrowserScreenActions,
    swipeToOpen: Boolean,
    panelWidth: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val fraction = remember { Animatable(if (navigation.modalOpen) 1f else 0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val widthPx = with(LocalDensity.current) { panelWidth.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentActions by rememberUpdatedState(actions)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(navigation.modalOpen) {
        if (dragging) return@LaunchedEffect
        fraction.animateTo(if (navigation.modalOpen) 1f else 0f, spatial)
    }
    fun settle(velocity: Float) {
        dragging = false
        val directed = if (rtl) -velocity else velocity
        val open = when {
            directed > 800f -> true
            directed < -800f -> false
            else -> fraction.value > 0.5f
        }
        scope.launch { fraction.animateTo(if (open) 1f else 0f, spatial) }
        if (open != navigation.modalOpen) currentActions.onNavigationOpenChange(open)
    }
    val dragState = rememberDraggableState { delta ->
        val directed = if (rtl) -delta else delta
        scope.launch { fraction.snapTo((fraction.value + directed / widthPx).coerceIn(0f, 1f)) }
    }
    val open = navigation.modalOpen || fraction.value > 0f
    Box(
        Modifier.fillMaxSize().draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            enabled = swipeToOpen || open,
            onDragStarted = { dragging = true },
            onDragStopped = { settle(it) }
        )
    ) {
        content()
        if (open) {
            PredictiveBackHandler(enabled = navigation.modalOpen) { events ->
                try {
                    events.collect { fraction.snapTo(1f - it.progress) }
                    fraction.animateTo(0f, fast)
                    currentActions.onNavigationOpenChange(false)
                } catch (e: CancellationException) {
                    fraction.animateTo(1f, spatial)
                    throw e
                }
            }
            val scrim = MaterialTheme.colorScheme.scrim
            val closeLabel = stringResource(R.string.close)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = fraction.value }
                    .background(scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = closeLabel) {
                        currentActions.onNavigationOpenChange(false)
                    }
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxHeight().width(panelWidth)
                    .graphicsLayer {
                        val offset = (1f - fraction.value) * (size.width + 16.dp.toPx())
                        translationX = if (rtl) offset else -offset
                    }
                    .semantics { paneTitle = closeLabel }
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp && it.key == Key.Escape) {
                            currentActions.onNavigationOpenChange(false); true
                        } else false
                    }
            ) {
                Column(
                    Modifier.windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
                    )
                ) {
                    VFilesNavigationRailHeader(
                        expanded = true,
                        onToggle = { currentActions.onNavigationOpenChange(false) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    VFilesNavigationRailContent(
                        items = navigation.items,
                        currentPath = navigation.currentPath,
                        revision = navigation.revision,
                        expanded = true,
                        listener = navigationListener,
                        onAddBookmark = {
                            currentActions.onFolderAction(FileBrowserFolderAction.ADD_BOOKMARK)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserScaffold(
    chrome: FileBrowserChromeState,
    content: FileBrowserContentState,
    jobs: List<FileJobProgress>,
    createMenuExpanded: Boolean,
    actions: FileBrowserScreenActions,
    showNavigationIcon: Boolean,
    navigationOpen: Boolean,
    modifier: Modifier
) {
    val searchBarState = rememberSearchBarState(
        initialValue = if (chrome.searchExpanded) SearchBarValue.Expanded
            else SearchBarValue.Collapsed
    )
    val textFieldState = rememberTextFieldState(chrome.searchQuery)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentActions by rememberUpdatedState(actions)
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    var folderSheetOpen by rememberSaveable { mutableStateOf(false) }
    var scrollToTopToken by remember { mutableIntStateOf(0) }
    val folderBack = remember { Animatable(0f) }
    val selectionMode = chrome.selectedCount > 0
    val pasteMode = !selectionMode && chrome.pasteCount > 0
    val displayed = rememberDisplayedFiles(content)
    // Everything between the first and last selected file, in on-screen order; null when the
    // span is already fully selected or a single-choice picker forbids it.
    val rangeFiles = remember(displayed, content.selectedPaths, chrome.picker, chrome.pickerCanSelectMultiple) {
        val files = displayed?.files
        if (files == null || content.selectedPaths.size < 2 ||
            (chrome.picker && !chrome.pickerCanSelectMultiple)) return@remember null
        val first = files.indexOfFirst { it.path in content.selectedPaths }
        val last = files.indexOfLast { it.path in content.selectedPaths }
        if (first < 0 || last <= first) return@remember null
        files.subList(first, last + 1).takeIf { range ->
            range.any { it.path !in content.selectedPaths }
        }
    }

    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.targetValue == SearchBarValue.Expanded }
            .distinctUntilChanged()
            .drop(1)
            .collect { expanded ->
                if (!expanded) textFieldState.clearText()
                currentActions.onSearchActiveChange(expanded)
            }
    }
    // The host collapses search when it navigates (e.g. a folder opened from the results).
    LaunchedEffect(chrome.searchExpanded) {
        if (!chrome.searchExpanded && searchBarState.targetValue == SearchBarValue.Expanded) {
            searchBarState.animateToCollapsed()
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { query ->
                currentActions.onSearchQueryChange(query)
                if (query.isNotBlank()) {
                    delay(SEARCH_DEBOUNCE_MILLIS)
                    currentActions.onSearchSubmit(query)
                }
            }
    }
    BackHandler(enabled = selectionMode) { actions.onSelection(FileBrowserSelectionCommand.CLEAR) }
    // Back to the parent folder previews the move: the list follows the gesture, and on commit
    // the parent slides in from the start edge with the regular folder transition.
    PredictiveBackHandler(
        enabled = chrome.canNavigateUp && !selectionMode && !chrome.searchExpanded &&
            !navigationOpen && !createMenuExpanded
    ) { events ->
        try {
            events.collect { folderBack.snapTo(it.progress) }
            currentActions.onFolderAction(FileBrowserFolderAction.NAVIGATE_UP)
            // Let the leaving page keep its pose while it fades out.
            delay(400)
            folderBack.snapTo(0f)
        } catch (e: CancellationException) {
            folderBack.animateTo(0f, MaterialTheme_fastSpatial)
            throw e
        }
    }
    BackHandler(enabled = createMenuExpanded) { actions.onCreateMenuExpandedChange(false) }

    val searchExpanded = searchBarState.currentValue == SearchBarValue.Expanded
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { query ->
                keyboard?.hide()
                if (query.isNotBlank()) actions.onSearchSubmit(query)
            },
            placeholder = {
                Text(
                    stringResource(
                        if (searchExpanded) R.string.file_browser_search_in_format
                        else R.string.file_browser_search_placeholder,
                        chrome.title
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                if (searchExpanded) {
                    IconButton(
                        onClick = { scope.launch { searchBarState.animateToCollapsed() } },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(R.drawable.settings_expressive_back_24dp),
                            contentDescription = stringResource(R.string.file_browser_close_search)
                        )
                    }
                } else {
                    Icon(painterResource(R.drawable.file_browser_icon_search), contentDescription = null)
                }
            },
            trailingIcon = if (searchExpanded && textFieldState.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { textFieldState.clearText() },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(R.drawable.file_browser_icon_close),
                            contentDescription = stringResource(R.string.file_browser_clear_search)
                        )
                    }
                }
            } else null
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (showNavigationIcon) ScaffoldDefaults.contentWindowInsets
            // The persistent rail already pads the start edge.
            else ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Vertical + WindowInsetsSides.End
            ),
        topBar = {
            BrowserHeader(
                chrome = chrome,
                scrollBehavior = scrollBehavior,
                searchBarState = searchBarState,
                inputField = inputField,
                windowInsets = WindowInsets.safeDrawing.only(
                    if (showNavigationIcon) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top + WindowInsetsSides.End
                ),
                showNavigationIcon = showNavigationIcon,
                selectionMode = selectionMode,
                onSelectRange = rangeFiles?.let { files -> { actions.onRangeSelect(files) } },
                actions = actions,
                onOpenSort = { sortSheetOpen = true },
                onOpenOverflow = { folderSheetOpen = true },
                onScrollToTop = {
                    scrollToTopToken++
                    val state = scrollBehavior.state
                    scope.launch {
                        animate(state.heightOffset, 0f) { value, _ -> state.heightOffset = value }
                    }
                }
            )
            ExpandedFullScreenContainedSearchBar(state = searchBarState, inputField = inputField) {
                BrowserSearchResults(
                    query = textFieldState.text.toString(),
                    chrome = chrome,
                    content = content,
                    actions = actions
                )
            }
        }
    ) { padding ->
        val fabVisible = !chrome.picker && !chrome.currentReadOnly && !selectionMode &&
            !pasteMode && !chrome.searchExpanded
        val pickerBarVisible = chrome.picker && (selectionMode ||
            chrome.pickerMode == PickOptions.Mode.OPEN_DIRECTORY ||
            chrome.pickerMode == PickOptions.Mode.CREATE_FILE)
        val toolbarVisible = (selectionMode && !chrome.picker) || pasteMode || pickerBarVisible
        // The list sits below the header, never under it: the header is opaque and fixed, and
        // its height only changes through the list's own scroll.
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            VFilesBrowserContent(
                state = content,
                onRefresh = actions::onRefresh,
                onItemAction = actions::onItemAction,
                onRangeSelect = actions::onRangeSelect,
                displayed = displayed,
                onSetSelection = actions::onSetSelection,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                // Room for the FAB or floating toolbar so the last row is never covered.
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = 0.dp,
                    bottom = padding.calculateBottomPadding() + 112.dp
                ),
                scrollToTopToken = scrollToTopToken,
                backProgress = { folderBack.value },
                picker = chrome.picker
            )
            if (createMenuExpanded) {
                // Outside taps close the FAB menu without reaching the rows underneath.
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.file_browser_create_close)
                    ) { actions.onCreateMenuExpandedChange(false) }
                )
            }
            Box(
                Modifier.fillMaxSize().padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding()
                )
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (toolbarVisible) {
                        VFilesJobStatus(jobs, actions::onCancelJob)
                    }
                    AnimatedVisibility(
                        visible = selectionMode && !chrome.picker,
                        enter = floatingToolbarEnter(),
                        exit = floatingToolbarExit()
                    ) {
                        BrowserSelectionToolbar(chrome, actions::onSelection)
                    }
                    AnimatedVisibility(
                        visible = pasteMode,
                        enter = floatingToolbarEnter(),
                        exit = floatingToolbarExit()
                    ) {
                        BrowserPasteToolbar(chrome, actions::onSelection)
                    }
                    AnimatedVisibility(
                        visible = pickerBarVisible,
                        enter = floatingToolbarEnter(),
                        exit = floatingToolbarExit()
                    ) {
                        BrowserPickerBar(chrome, actions)
                    }
                }
                if (!toolbarVisible) {
                    VFilesJobStatus(
                        jobs, actions::onCancelJob,
                        Modifier.align(Alignment.BottomStart).padding(16.dp)
                    )
                }
                AnimatedVisibility(
                    visible = fabVisible,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                ) {
                    VFilesCreateMenu(
                        enabled = true,
                        expanded = createMenuExpanded,
                        onExpandedChange = actions::onCreateMenuExpandedChange,
                        onCreateFile = actions::onCreateFile,
                        onCreateDirectory = actions::onCreateDirectory
                    )
                }
            }
        }
    }
    if (sortSheetOpen) {
        BrowserViewSortSheet(state = chrome, actions = actions, onDismiss = { sortSheetOpen = false })
    }
    if (folderSheetOpen) {
        BrowserFolderSheet(
            state = chrome,
            onAction = actions::onFolderAction,
            onOpenViewSort = { sortSheetOpen = true },
            onDismiss = { folderSheetOpen = false }
        )
    }
}

private val MaterialTheme_fastSpatial =
    androidx.compose.animation.core.spring<Float>(dampingRatio = 0.9f, stiffness = 1400f)

/**
 * External picker controls in the browser's floating toolbar position: pick the selection,
 * use the folder on screen, or name the file to save.
 */
@Composable
private fun BrowserPickerBar(chrome: FileBrowserChromeState, actions: FileBrowserScreenActions) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 640.dp).fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                chrome.selectedCount > 0 -> {
                    Text(
                        pluralStringResource(
                            R.plurals.file_browser_selected_count, chrome.selectedCount,
                            chrome.selectedCount
                        ),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { actions.onSelection(FileBrowserSelectionCommand.PICK) },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(
                            if (chrome.pickerMode == PickOptions.Mode.CREATE_FILE) R.string.save
                            else R.string.select
                        ))
                    }
                }
                chrome.pickerMode == PickOptions.Mode.OPEN_DIRECTORY -> {
                    Text(
                        chrome.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = actions::onPickCurrentDirectory, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.select), maxLines = 1)
                    }
                }
                chrome.pickerMode == PickOptions.Mode.CREATE_FILE -> {
                    var name by rememberSaveable(chrome.pickerFileName, stateSaver = TextFieldValue.Saver) {
                        val base = chrome.pickerFileName.substringBeforeLast('.')
                        mutableStateOf(TextFieldValue(chrome.pickerFileName, TextRange(0, base.length)))
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.file_browser_dialog_name_label)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { actions.onPickCreateFile(name.text) }),
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp)
                    )
                    Button(
                        onClick = { actions.onPickCreateFile(name.text) },
                        shapes = ButtonDefaults.shapes()
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/**
 * Floating toolbars rise from below their resting place and settle with the expressive spatial
 * spring, growing out of the bottom edge; they leave faster than they arrive.
 */
@Composable
private fun floatingToolbarEnter(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return slideInVertically(motion.defaultSpatialSpec()) { it / 2 } +
        scaleIn(
            motion.defaultSpatialSpec(), initialScale = 0.8f,
            transformOrigin = TransformOrigin(0.5f, 1f)
        ) +
        fadeIn(motion.defaultEffectsSpec())
}

@Composable
private fun floatingToolbarExit(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return slideOutVertically(motion.fastSpatialSpec()) { it / 2 } +
        scaleOut(
            motion.fastSpatialSpec(), targetScale = 0.8f,
            transformOrigin = TransformOrigin(0.5f, 1f)
        ) +
        fadeOut(motion.fastEffectsSpec())
}

/**
 * Recursive search results from the current folder. Results reuse the browser rows, but
 * selection is off here: there is no selection toolbar over the full-screen search surface.
 */
@Composable
private fun BrowserSearchResults(
    query: String,
    chrome: FileBrowserChromeState,
    content: FileBrowserContentState,
    actions: FileBrowserScreenActions
) {
    when {
        query.isBlank() -> BrowserEmptyState(
            icon = R.drawable.file_browser_icon_search,
            title = stringResource(R.string.file_browser_search_hint_title),
            detail = stringResource(R.string.file_browser_search_hint_detail, chrome.title),
            actionLabel = null,
            onAction = null
        )
        !content.searching -> BrowserLoadingState()
        else -> VFilesBrowserContent(
            state = content.copy(viewType = FileViewType.LIST),
            onRefresh = actions::onRefresh,
            onItemAction = actions::onItemAction,
            onRangeSelect = {},
            selectionEnabled = false,
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    16.dp
            )
        )
    }
}

/** Status and navigation bar icons follow the Compose surface, not the hidden legacy app bar. */
@Composable
private fun SystemBarAppearance() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
