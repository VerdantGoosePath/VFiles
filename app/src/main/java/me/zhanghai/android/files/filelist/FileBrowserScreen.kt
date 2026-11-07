@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import java8.nio.file.Path
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
    fun onSelection(command: FileBrowserSelectionCommand)
    fun onFolderAction(action: FileBrowserFolderAction)
    fun onCreateMenuExpandedChange(expanded: Boolean)
    fun onCreateFile()
    fun onCreateDirectory()
    fun onCancelJob(id: Int)
    fun onNavigationOpenChange(open: Boolean)
    fun onTogglePersistentNavigation()
}

/** Typing pauses this long before a recursive provider search starts. */
private const val SEARCH_DEBOUNCE_MILLIS = 350L

/**
 * The whole browser as one expressive surface: navigation rail, search app bar, folder header,
 * files, floating toolbars and the FAB menu share one theme, one inset model and one motion
 * scheme instead of being Compose islands inside legacy Views.
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
                        listener = navigationListener
                    )
                }
                BrowserScaffold(
                    chrome, content, jobs, createMenuExpanded, actions,
                    showNavigationIcon = false,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            BrowserScaffold(
                chrome, content, jobs, createMenuExpanded, actions,
                showNavigationIcon = true,
                modifier = Modifier.fillMaxSize()
            )
            val modalState = rememberWideNavigationRailState()
            LaunchedEffect(navigation.modalOpen) {
                if (navigation.modalOpen) modalState.expand() else modalState.collapse()
            }
            val currentActions by rememberUpdatedState(actions)
            // A scrim tap or Back collapses the rail itself; report it so the host agrees.
            LaunchedEffect(modalState) {
                snapshotFlow { modalState.targetValue == WideNavigationRailValue.Expanded }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { currentActions.onNavigationOpenChange(it) }
            }
            ModalWideNavigationRail(
                state = modalState,
                hideOnCollapse = true,
                header = {
                    VFilesNavigationRailHeader(
                        expanded = true,
                        onToggle = { actions.onNavigationOpenChange(false) }
                    )
                }
            ) {
                VFilesNavigationRailContent(
                    items = navigation.items,
                    currentPath = navigation.currentPath,
                    revision = navigation.revision,
                    expanded = true,
                    listener = navigationListener
                )
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
    modifier: Modifier
) {
    val searchBarState = rememberSearchBarState(
        initialValue = if (chrome.searchExpanded) SearchBarValue.Expanded
            else SearchBarValue.Collapsed
    )
    val textFieldState = rememberTextFieldState(chrome.searchQuery)
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentActions by rememberUpdatedState(actions)
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    val selectionMode = chrome.selectedCount > 0
    val pasteMode = !selectionMode && chrome.pasteCount > 0

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
                    stringResource(R.string.file_browser_search_in_format, chrome.title),
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
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = if (showNavigationIcon) ScaffoldDefaults.contentWindowInsets
            // The persistent rail already pads the start edge.
            else ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Vertical + WindowInsetsSides.End
            ),
        topBar = {
            val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                label = "browserTopBar"
            ) { selecting ->
                if (selecting) {
                    BrowserSelectionTopBar(chrome, actions::onSelection)
                } else {
                    AppBarWithSearch(
                        state = searchBarState,
                        inputField = inputField,
                        scrollBehavior = scrollBehavior,
                        windowInsets = if (showNavigationIcon) SearchBarDefaults.windowInsets
                            else SearchBarDefaults.windowInsets.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End
                            ),
                        navigationIcon = if (showNavigationIcon) {
                            {
                                BrowserIconAction(
                                    R.drawable.menu_icon_control_normal_24dp,
                                    stringResource(R.string.open_navigation_drawer)
                                ) { actions.onNavigationOpenChange(true) }
                            }
                        } else null,
                        actions = { BrowserOverflowMenu(chrome, actions::onFolderAction) }
                    )
                }
            }
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
        val toolbarVisible = selectionMode || pasteMode
        Box(Modifier.fillMaxSize()) {
            VFilesBrowserContent(
                state = content,
                onRefresh = actions::onRefresh,
                onItemAction = actions::onItemAction,
                onRangeSelect = actions::onRangeSelect,
                // Room for the FAB or floating toolbar so the last row is never covered.
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 112.dp
                ),
                header = {
                    BrowserLocationHeader(
                        state = chrome,
                        onBreadcrumb = actions::onBreadcrumb,
                        onViewType = actions::onViewType,
                        onOpenSort = { sortSheetOpen = true }
                    )
                }
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
                        visible = selectionMode,
                        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                    ) {
                        BrowserSelectionToolbar(chrome, actions::onSelection)
                    }
                    AnimatedVisibility(
                        visible = pasteMode,
                        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                    ) {
                        BrowserPasteToolbar(chrome, actions::onSelection)
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
        BrowserSortSheet(
            state = chrome,
            onDismiss = { sortSheetOpen = false },
            onSortBy = actions::onSortBy,
            onSortOrder = actions::onSortOrder,
            onDirectoriesFirst = actions::onDirectoriesFirst,
            onPathSpecificSort = actions::onPathSpecificSort,
            onShowHiddenFiles = actions::onShowHiddenFiles
        )
    }
}

/** Contextual app bar while files are selected: count, close and select all. */
@Composable
private fun BrowserSelectionTopBar(
    chrome: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit
) {
    val commands = chrome.commandContext().availableCommands()
    TopAppBar(
        title = {
            Text(
                pluralStringResource(
                    R.plurals.file_browser_selected_count, chrome.selectedCount,
                    chrome.selectedCount
                ),
                style = MaterialTheme.typography.titleLargeEmphasized
            )
        },
        navigationIcon = {
            BrowserIconAction(
                R.drawable.file_browser_icon_close,
                stringResource(R.string.file_browser_clear_selection)
            ) { onSelection(FileBrowserSelectionCommand.CLEAR) }
        },
        actions = {
            if (FileBrowserSelectionCommand.SELECT_ALL in commands) {
                BrowserIconAction(
                    R.drawable.select_all_icon_control_normal_24dp,
                    stringResource(R.string.select_all)
                ) { onSelection(FileBrowserSelectionCommand.SELECT_ALL) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

@Composable
private fun BrowserIconAction(icon: Int, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below
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
