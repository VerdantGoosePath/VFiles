@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.settings.ItemCountStyle
import kotlin.math.roundToInt

/** The bar row keeps one height in every mode, so swapping it never moves the list below. */
private val BarHeight = 64.dp

/**
 * A flexible app bar that stays above the list instead of scrolling inside it. Expanded, it
 * shows the search bar, then the folder's path, name, item counts and view controls. As the
 * files scroll, the expanded part folds away under the bar: the name moves into the bar and
 * search shrinks to an icon button. The status bar area is always painted, so files never show
 * through behind it.
 */
@Composable
internal fun BrowserHeader(
    chrome: FileBrowserChromeState,
    scrollBehavior: TopAppBarScrollBehavior,
    searchBarState: SearchBarState,
    inputField: @Composable () -> Unit,
    windowInsets: WindowInsets,
    showNavigationIcon: Boolean,
    selectionMode: Boolean,
    onSelectRange: (() -> Unit)?,
    actions: FileBrowserScreenActions,
    onOpenSort: () -> Unit,
    onOpenOverflow: () -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = scrollBehavior.state
    val fraction = state.collapsedFraction
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>()
    val barTint by animateColorAsState(
        if (selectionMode) colors.secondaryContainer else colors.surface.copy(alpha = 0f),
        effects
    )
    val haptics = LocalHapticFeedback.current
    val currentScrollToTop by rememberUpdatedState(onScrollToTop)
    // A new folder opens at its top, so its header opens fully too.
    val locationKey = chrome.breadcrumbs.getOrNull(chrome.currentIndex)?.id to chrome.title
    LaunchedEffect(locationKey) {
        if (state.heightOffset != 0f) {
            animate(state.heightOffset, 0f) { value, _ -> state.heightOffset = value }
        }
    }
    Surface(
        color = lerp(colors.surface, colors.surfaceContainer, fraction),
        modifier = modifier.fillMaxWidth().then(
            if (chrome.longPressScrollToTop) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentScrollToTop()
                    })
                }
            } else Modifier
        )
    ) {
        Column {
            Column(Modifier.background(barTint).windowInsetsPadding(windowInsets)) {
                Box(Modifier.fillMaxWidth().height(BarHeight)) {
                    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = selectionMode,
                        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                        label = "browserBar"
                    ) { selecting ->
                        if (selecting) {
                            BrowserSelectionBar(chrome, actions::onSelection, onSelectRange)
                        } else {
                            BrowserBar(
                                chrome, fraction, searchBarState, inputField, showNavigationIcon,
                                actions, onOpenOverflow
                            )
                        }
                    }
                }
            }
            // Only the sides: the bar above already sits below the status bar.
            CollapsingSection(
                scrollBehavior,
                Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            ) {
                BrowserLocationDetails(
                    chrome = chrome,
                    onBreadcrumb = actions::onBreadcrumb,
                    onViewType = actions::onViewType,
                    onOpenSort = onOpenSort,
                    modifier = Modifier.graphicsLayer {
                        alpha = (1f - state.collapsedFraction * 1.6f).coerceIn(0f, 1f)
                    }
                )
            }
        }
    }
}

/** Measures its content once and shows only what the collapse offset leaves, sliding it up. */
@Composable
private fun CollapsingSection(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val state = scrollBehavior.state
        val limit = -placeable.height.toFloat()
        if (state.heightOffsetLimit != limit) {
            state.heightOffsetLimit = limit
            // Re-coerce against the new limit (e.g. after the details grew or shrank).
            state.heightOffset = state.heightOffset
        }
        val height = (placeable.height + state.heightOffset).roundToInt()
            .coerceIn(0, placeable.height)
        layout(constraints.maxWidth, height) { placeable.place(0, height - placeable.height) }
    }
}

@Composable
private fun BrowserBar(
    chrome: FileBrowserChromeState,
    fraction: Float,
    searchBarState: SearchBarState,
    inputField: @Composable () -> Unit,
    showNavigationIcon: Boolean,
    actions: FileBrowserScreenActions,
    onOpenOverflow: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val collapsed = fraction >= 0.5f
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showNavigationIcon) {
            BrowserIconAction(
                R.drawable.menu_icon_control_normal_24dp,
                stringResource(R.string.open_navigation_drawer)
            ) { actions.onNavigationOpenChange(true) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            val searchAlpha = (1f - fraction * 2f).coerceIn(0f, 1f)
            // Stays composed while hidden: the full-screen search grows out of its bounds.
            SearchBar(
                state = searchBarState,
                inputField = inputField,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    .graphicsLayer { alpha = searchAlpha }
                    .then(if (collapsed) Modifier.clearAndSetSemantics { } else Modifier)
            )
            if (collapsed) {
                // Swallows touches meant for the invisible search bar beneath the title.
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
                )
                val titleAlpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
                Text(
                    chrome.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(horizontal = 12.dp).graphicsLayer {
                        alpha = titleAlpha
                        translationY = (1f - titleAlpha) * 8.dp.toPx()
                    }.semantics { heading() }
                )
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = collapsed,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        ) {
            BrowserIconAction(
                R.drawable.file_browser_icon_search,
                stringResource(R.string.file_browser_search_action)
            ) { scope.launch { searchBarState.animateToExpanded() } }
        }
        if (chrome.showUpButton) {
            BrowserIconAction(
                R.drawable.arrow_up_icon_control_normal_24dp,
                stringResource(R.string.file_list_action_navigate_up),
                enabled = chrome.canNavigateUp
            ) { actions.onFolderAction(FileBrowserFolderAction.NAVIGATE_UP) }
        }
        BrowserIconAction(
            R.drawable.file_browser_icon_more,
            stringResource(R.string.file_browser_more_options),
            onClick = onOpenOverflow
        )
    }
}

/** Contextual bar while files are selected: count, close, range and select all. */
@Composable
private fun BrowserSelectionBar(
    chrome: FileBrowserChromeState,
    onSelection: (FileBrowserSelectionCommand) -> Unit,
    onSelectRange: (() -> Unit)?
) {
    val commands = chrome.commandContext().availableCommands()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserIconAction(
                R.drawable.file_browser_icon_close,
                stringResource(R.string.file_browser_clear_selection)
            ) { onSelection(FileBrowserSelectionCommand.CLEAR) }
            Text(
                pluralStringResource(
                    R.plurals.file_browser_selected_count, chrome.selectedCount,
                    chrome.selectedCount
                ),
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            if (onSelectRange != null) {
                BrowserIconAction(
                    R.drawable.select_range_icon_24dp,
                    stringResource(R.string.file_browser_select_range),
                    onClick = onSelectRange
                )
            }
            if (FileBrowserSelectionCommand.SELECT_ALL in commands) {
                BrowserIconAction(
                    R.drawable.select_all_icon_control_normal_24dp,
                    stringResource(R.string.select_all)
                ) { onSelection(FileBrowserSelectionCommand.SELECT_ALL) }
            }
        }
    }
}

@Composable
internal fun BrowserIconAction(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/**
 * The expanded part of the header: the folder name with its contents, then where it sits, then
 * how it is shown. The path is its own contained bar below the name, so the name leads and the
 * path reads as a tool rather than a caption.
 */
@Composable
private fun BrowserLocationDetails(
    chrome: FileBrowserChromeState,
    onBreadcrumb: (String) -> Unit,
    onViewType: (FileViewType) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        Text(
            chrome.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.semantics { heading() }
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrowserItemCounts(chrome, Modifier.weight(1f, fill = false))
            // Read-only belongs to the folder: state it once here, not on every row.
            if (chrome.currentReadOnly && !chrome.picker) BrowserReadOnlyLabel()
        }
        // A one-folder path would only repeat the name above it.
        if (chrome.breadcrumbs.size > 1) {
            val current = chrome.currentIndex.takeIf { it in chrome.breadcrumbs.indices }
                ?: chrome.breadcrumbs.lastIndex
            BrowserPathBar(
                chrome.breadcrumbs, current, onBreadcrumb, Modifier.padding(top = 12.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrowserSortButton(chrome.sortOptions, onOpenSort, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.001f))
            if (chrome.viewTogglePlacement.inHeader) {
                BrowserViewTypeToggle(chrome.viewType, onViewType)
            }
        }
    }
}

/**
 * The full path, current folder included, in one contained bar. Every crumb has the same shape;
 * only its colour says where it is: the current folder is emphasized, the folders above it are
 * muted, and folders left after navigating up are fainter still but stay one tap away.
 */
@Composable
private fun BrowserPathBar(
    breadcrumbs: List<BreadcrumbUi>,
    currentIndex: Int,
    onBreadcrumb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState(initial = Int.MAX_VALUE)
    var viewportWidth by remember { mutableIntStateOf(0) }
    var currentBounds by remember { mutableStateOf(IntRange.EMPTY) }
    val peek = with(LocalDensity.current) { 48.dp.roundToPx() }
    LaunchedEffect(currentIndex, breadcrumbs.size, currentBounds, viewportWidth) {
        if (viewportWidth == 0) return@LaunchedEffect
        if (currentBounds.isEmpty() || currentIndex >= breadcrumbs.lastIndex) {
            scroll.animateScrollTo(scroll.maxValue)
            return@LaunchedEffect
        }
        // Show the whole current crumb plus a hint of any forward crumbs after it.
        val trailing = if (currentIndex < breadcrumbs.lastIndex) peek else 0
        val target = (currentBounds.last + trailing - viewportWidth)
            .coerceIn(0, scroll.maxValue)
        val value = minOf(currentBounds.first, target)
        if (value != scroll.value) scroll.animateScrollTo(value)
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)
    ) {
        Row(
            modifier = Modifier.onSizeChanged { viewportWidth = it.width }
                .scrollEdgeFade(scroll)
                .horizontalScroll(scroll)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbs.forEachIndexed { index, segment ->
                val current = index == currentIndex
                val forward = index > currentIndex
                if (index > 0) {
                    Icon(
                        painterResource(R.drawable.arrow_end_icon_white_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.outline
                    )
                }
                Text(
                    segment.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        current -> colors.onSurface
                        forward -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> colors.onSurfaceVariant
                    },
                    style = if (current) MaterialTheme.typography.labelLargeEmphasized
                        else MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !current, role = Role.Button) { onBreadcrumb(segment.id) }
                        .semantics { selected = current }
                        .padding(horizontal = CrumbPadding, vertical = 10.dp)
                        .then(
                            if (current) Modifier.onPlaced {
                                val x = it.positionInParent().x.toInt()
                                currentBounds = x..(x + it.size.width)
                            } else Modifier
                        )
                )
            }
        }
    }
}

private val CrumbPadding = 8.dp
private val EdgeFadeWidth = 24.dp

/** Fades whichever edge still has crumbs scrolled out of view, so clipping reads as "more". */
private fun Modifier.scrollEdgeFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = EdgeFadeWidth.toPx().coerceAtMost(size.width / 2)
            if (scroll.canScrollBackward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                    size = Size(fade, size.height), blendMode = BlendMode.DstIn
                )
            }
            if (scroll.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent), size.width - fade, size.width
                    ),
                    topLeft = Offset(size.width - fade, 0f), size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn
                )
            }
        }

/** Folder and file counts as icons, words, or both, following the user's preference. */
@Composable
private fun BrowserItemCounts(chrome: FileBrowserChromeState, modifier: Modifier = Modifier) {
    val folders = chrome.folderCount
    val files = chrome.fileCount
    val style = MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (folders == null || files == null) {
        if (chrome.subtitle.isNotEmpty()) {
            Text(chrome.subtitle, style = style, color = color, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = modifier)
        }
        return
    }
    if (folders == 0 && files == 0) {
        Text(stringResource(R.string.file_browser_empty_description), style = style,
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val folderText = pluralStringResource(
        R.plurals.file_list_subtitle_directory_count_format, folders, folders
    )
    val fileText = pluralStringResource(
        R.plurals.file_list_subtitle_file_count_format, files, files
    )
    val description = listOfNotNull(
        folderText.takeIf { folders > 0 }, fileText.takeIf { files > 0 }
    ).joinToString(", ")
    if (chrome.itemCountStyle == ItemCountStyle.TEXT) {
        Text(
            listOfNotNull(folderText.takeIf { folders > 0 }, fileText.takeIf { files > 0 })
                .joinToString(" · "),
            style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }
    val withText = chrome.itemCountStyle == ItemCountStyle.BOTH
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (folders > 0) {
            CountChip(
                R.drawable.folder_icon_control_normal_24dp,
                if (withText) folderText else "%,d".format(folders), style, color
            )
        }
        if (files > 0) {
            CountChip(
                R.drawable.browser_icon_file,
                if (withText) fileText else "%,d".format(files), style, color
            )
        }
    }
}

@Composable
private fun CountChip(
    icon: Int,
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color,
            modifier = Modifier.size(18.dp))
        Text(text, style = style, color = color, maxLines = 1)
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

/** Tonal button naming the sort key, with an arrow for the direction; opens the sheet. */
@Composable
private fun BrowserSortButton(
    options: FileSortOptions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(sortByLabel(options.by))
    val direction = stringResource(sortDirectionLabel(options))
    val height = ButtonDefaults.MinHeight
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true, hasEndIcon = true),
        modifier = modifier.heightIn(min = height).semantics {
            contentDescription = "$label, $direction"
        }
    ) {
        Icon(
            painterResource(R.drawable.file_browser_icon_sort),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height))
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(4.dp))
        Icon(
            painterResource(
                if (options.order == FileSortOptions.Order.ASCENDING) R.drawable.browser_icon_arrow_upward
                else R.drawable.browser_icon_arrow_downward
            ),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height))
        )
    }
}

/** Connected list/grid toggle with labels: one control, two exclusive layouts ([C21]). */
@Composable
internal fun BrowserViewTypeToggle(
    viewType: FileViewType,
    onViewType: (FileViewType) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        listOf(FileViewType.LIST, FileViewType.GRID).forEachIndexed { index, type ->
            val checked = viewType == type
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (it) onViewType(type) },
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                contentPadding = ToggleButtonDefaults.contentPaddingFor(
                    ToggleButtonDefaults.size, hasStartIcon = true
                ),
                modifier = (if (fill) Modifier.weight(1f) else Modifier).semantics {
                    role = Role.RadioButton
                    selected = checked
                }
            ) {
                Icon(
                    painterResource(
                        if (type == FileViewType.LIST) R.drawable.file_browser_icon_list
                        else R.drawable.file_browser_icon_grid
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        if (type == FileViewType.LIST) R.string.file_list_action_view_list
                        else R.string.file_list_action_view_grid
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

internal fun sortByLabel(by: FileSortOptions.By): Int = when (by) {
    FileSortOptions.By.NAME -> R.string.file_list_action_sort_by_name
    FileSortOptions.By.TYPE -> R.string.file_list_action_sort_by_type
    FileSortOptions.By.SIZE -> R.string.file_list_action_sort_by_size
    FileSortOptions.By.LAST_MODIFIED -> R.string.file_list_action_sort_by_last_modified
}

/** Plain-language direction for the active key, e.g. "Newest first". */
internal fun sortDirectionLabel(options: FileSortOptions): Int {
    val ascending = options.order == FileSortOptions.Order.ASCENDING
    return when (options.by) {
        FileSortOptions.By.NAME, FileSortOptions.By.TYPE ->
            if (ascending) R.string.file_browser_sort_a_to_z else R.string.file_browser_sort_z_to_a
        FileSortOptions.By.SIZE ->
            if (ascending) R.string.file_browser_sort_smallest_first
            else R.string.file_browser_sort_largest_first
        FileSortOptions.By.LAST_MODIFIED ->
            if (ascending) R.string.file_browser_sort_oldest_first
            else R.string.file_browser_sort_newest_first
    }
}

internal fun sortByIcon(by: FileSortOptions.By): Int = when (by) {
    FileSortOptions.By.NAME -> R.drawable.sort_by_name_icon_control_normal_24dp
    FileSortOptions.By.TYPE -> R.drawable.sort_by_type_icon_control_normal_24dp
    FileSortOptions.By.SIZE -> R.drawable.sort_by_size_icon_control_normal_24dp
    FileSortOptions.By.LAST_MODIFIED -> R.drawable.sort_by_date_icon_control_normal_24dp
}
