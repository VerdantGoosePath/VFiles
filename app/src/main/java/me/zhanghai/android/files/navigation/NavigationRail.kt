@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R

/**
 * Storage roots, standard folders and bookmarks rendered as expressive navigation rail items
 * ([C32]). The rail replaces the deprecated navigation drawer: modal and hidden on compact
 * windows, persistent and collapsible on medium and wider windows.
 *
 * Subtitles of local volumes query free disk space, so they resolve off the main thread and
 * refresh whenever [revision] changes. Every action still runs through the original
 * [NavigationItem] so provider-specific behaviour is preserved.
 */
@Composable
internal fun VFilesNavigationRailContent(
    items: List<NavigationItem?>,
    currentPath: Path?,
    revision: Int,
    expanded: Boolean,
    listener: NavigationItem.Listener,
    onAddBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loadedEntries by produceState<List<NavigationEntryUi>?>(
        initialValue = null, items, revision
    ) {
        value = withContext(Dispatchers.IO) {
            items.map { item ->
                if (item == null) NavigationEntryUi(null)
                else NavigationEntryUi(item, item.getTitle(context), item.getSubtitle(context))
            }
        }
    }
    val entries = loadedEntries
    // WideNavigationRail spaces and sizes its direct children; the items here live inside one
    // LazyColumn child, so the collapsed rail's 4dp gap and 64dp item height are applied here.
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val itemSpacing by animateDpAsState(if (expanded) 0.dp else 4.dp, spatialSpec)
    val itemMinHeight by animateDpAsState(if (expanded) 48.dp else 64.dp, spatialSpec)
    LazyColumn(
        // The rail measures its widest child; bound it so long volume names wrap instead of
        // pushing the rail across a phone screen.
        modifier = modifier.fillMaxHeight().widthIn(max = 300.dp).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (entries == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(36.dp))
                }
            }
            return@LazyColumn
        }
        itemsIndexed(
            entries,
            key = { index, entry -> entry.item?.id?.toString() ?: "divider-$index" },
            contentType = { _, entry ->
                when {
                    entry.item == null -> "divider"
                    entry.item.isHeader -> "header"
                    else -> "destination"
                }
            }
        ) { _, entry ->
            val item = entry.item
            when {
                item == null -> HorizontalDivider(
                    modifier = Modifier.padding(
                        horizontal = if (expanded) 24.dp else 28.dp, vertical = 8.dp
                    ),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                item.isHeader -> if (expanded) {
                    Text(
                        entry.title,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.isBookmarksHint -> AddBookmarkRailItem(
                    expanded, onAddBookmark, Modifier.heightIn(min = itemMinHeight)
                )
                else -> NavigationRailDestination(
                    entry, item, currentPath, expanded, listener,
                    Modifier.heightIn(min = itemMinHeight)
                )
            }
        }
    }
}

/**
 * Expanded destinations are full-width rows: the active indicator spans the rail, and titles
 * and subtitles ellipsize inside it instead of overflowing. The collapsed rail keeps the
 * standard icon-over-label item.
 */
@Composable
private fun NavigationRailDestination(
    entry: NavigationEntryUi,
    item: NavigationItem,
    currentPath: Path?,
    expanded: Boolean,
    listener: NavigationItem.Listener,
    modifier: Modifier = Modifier
) {
    // isChecked() consults the listener's current path and is provider-aware; reading
    // currentPath here makes the row recompose when the path changes.
    val selected = currentPath != null && item.isChecked(listener)
    val editLabel = stringResource(R.string.edit)
    val haptics = LocalHapticFeedback.current
    val longClick: (() -> Unit)? = if (item.supportsLongClick) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            item.onLongClick(listener)
        }
    } else null
    val longClickModifier = if (longClick != null) {
        Modifier.longPressBeforeClick(longClick).semantics {
            onLongClick(editLabel) { longClick(); true }
        }
    } else Modifier
    val icon: @Composable () -> Unit = {
        val res = item.iconResource
        if (res != null) Icon(painterResource(res), contentDescription = null)
    }
    Crossfade(
        targetState = expanded,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "railItem"
    ) { showExpanded ->
        if (showExpanded) {
            ExpandedRailRow(
                selected = selected,
                onClick = { item.onClick(listener) },
                icon = icon,
                title = entry.title,
                subtitle = entry.subtitle,
                modifier = modifier
                    .padding(start = if (item.isIndented) 16.dp else 0.dp)
                    .then(longClickModifier)
            )
        } else {
            WideNavigationRailItem(
                selected = selected,
                onClick = { item.onClick(listener) },
                railExpanded = false,
                modifier = modifier.then(longClickModifier),
                icon = icon,
                label = {
                    Text(
                        entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun ExpandedRailRow(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else Color.Transparent, effects
    )
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, effects
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** With no bookmarks yet, the rail offers to bookmark the folder on screen. */
@Composable
private fun AddBookmarkRailItem(
    expanded: Boolean,
    onAddBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.file_list_action_add_bookmark)
    Crossfade(
        targetState = expanded,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "railAddBookmark"
    ) { showExpanded ->
        if (showExpanded) {
            Box(modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                FilledTonalButton(
                    onClick = onAddBookmark,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.bookmark_add_icon_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            WideNavigationRailItem(
                selected = false,
                onClick = onAddBookmark,
                railExpanded = false,
                modifier = modifier,
                icon = { Icon(painterResource(R.drawable.bookmark_add_icon_24dp), contentDescription = null) },
                label = {
                    Text(
                        stringResource(R.string.navigation_bookmarks_hint_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp)
                    )
                }
            )
        }
    }
}

/**
 * The rail item owns the click; a long press must be detected before it without consuming the
 * initial down, then swallow the rest of the gesture so the release is not also a click.
 */
private fun Modifier.longPressBeforeClick(onLongPress: () -> Unit): Modifier = composed {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() >
                        viewConfiguration.touchSlop) {
                        break
                    }
                }
                true
            }
            if (released == null) {
                currentOnLongPress()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

/** Expanded-rail header: the toggle that collapses (or closes) the rail. */
@Composable
internal fun VFilesNavigationRailHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onToggle, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(
                    if (expanded) R.drawable.menu_open_icon_24dp
                    else R.drawable.menu_icon_control_normal_24dp
                ),
                contentDescription = stringResource(
                    if (expanded) R.string.file_browser_navigation_collapse
                    else R.string.file_browser_navigation_expand
                )
            )
        }
        if (expanded) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
