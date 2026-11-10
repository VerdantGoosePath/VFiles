@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.compose.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lets sheet content animate the sheet away before running the chosen action. */
@Stable
class VFilesSheetController internal constructor(
    private val state: SheetState,
    private val scope: CoroutineScope,
    private val onDismissed: () -> Unit
) {
    fun dismiss(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismissed()
            then()
        }
    }
}

/**
 * Modal sheet whose predictive back gesture slides the sheet down with the finger rather than
 * the platform default scale, then finishes the slide on commit. Every app sheet goes through
 * here so back feels identical everywhere.
 */
@Composable
fun VFilesBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.(VFilesSheetController) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentDismiss = rememberUpdatedState(onDismissRequest)
    val controller = remember(sheetState, scope) {
        VFilesSheetController(sheetState, scope) { currentDismiss.value() }
    }
    val backProgress = remember { Animatable(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Measured in the host window: the sheet's own window reports insets relative to itself.
    val topInset = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // A sheet that reaches the status bar gets top padding that shrinks as it is dragged, which
    // resizes it mid-drag and makes it bounce. Stopping short of the top keeps its size fixed.
    val maxContentHeight = (windowHeight - topInset - SheetTopGap - DragHandleHeight)
        .coerceAtLeast(0.dp)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        },
        // The built-in handler only scales; ours below translates instead.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        modifier = modifier
            .onSizeChanged { sheetHeight = it.height }
            .graphicsLayer { translationY = backProgress.value * sheetHeight * BackTravel }
    ) {
        PredictiveBackHandler(enabled = sheetState.isVisible) { events ->
            try {
                events.collect { backProgress.snapTo(it.progress) }
                controller.dismiss()
            } catch (e: CancellationException) {
                backProgress.animateTo(0f)
                throw e
            }
        }
        Column(Modifier.heightIn(max = maxContentHeight)) { content(controller) }
    }
}

/** Fraction of the sheet height the back gesture travels before release. */
private const val BackTravel = 0.5f

/** Scrim left visible above the tallest sheet, so it always reads as a sheet. */
private val SheetTopGap = 48.dp

/** [BottomSheetDefaults.DragHandle] with its vertical padding. */
private val DragHandleHeight = 48.dp

/** Scrollable body with the standard sheet gutters. */
@Composable
fun VFilesSheetColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Centered sheet title with its icon in a tonal circle, the M3E sheet headline pattern. */
@Composable
fun VFilesSheetHeader(
    icon: Int?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null)
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() }
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/** Primary-colored label that opens a group of segmented rows. */
@Composable
fun VFilesSheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/** A group of rows that read as one segmented container. */
@Composable
fun VFilesSheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content
    )
}

/** One action row of a sheet group: leading icon, label and optional trailing content. */
@Composable
fun VFilesSheetAction(
    icon: Int?,
    title: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = VFilesListDefaults.sheetSegmentedColors(),
        modifier = modifier,
        leadingContent = icon?.let {
            { Icon(painterResource(it), contentDescription = null, tint = tint) }
        },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailing,
        content = {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.error
                    else androidx.compose.ui.graphics.Color.Unspecified
            )
        }
    )
}
