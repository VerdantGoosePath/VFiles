package me.zhanghai.android.files.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stable ID is the provider/path identity, not list index. No paths read on main thread. */
data class FileEntryUi(
    val id: String,
    val name: String,
    val secondary: String = "",
    val directory: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = true
)

/**
 * File-list row shared contract. The ViewModel owns selection, actions, job execution and
 * provider IO. Long-press must have an accessible menu/checkbox alternative at call sites.
 * Stable IDs should be passed as LazyColumn item keys by screen owner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VFilesFileRow(
    entry: FileEntryUi,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { selected = entry.selected }
            .combinedClickable(
                enabled = entry.enabled, onClick = onOpen, onLongClick = onLongPress
            ),
        color = if (entry.selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leading != null) leading()
            else Text(if (entry.directory) "DIR" else "FILE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium,
                    color = if (entry.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (entry.secondary.isNotEmpty()) Text(entry.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.selected) Checkbox(checked = true, onCheckedChange = null)
        }
    }
}

/** Dense grid equivalent; the parent LazyVerticalGrid decides column count for window width. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VFilesFileGridTile(
    entry: FileEntryUi,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    thumbnail: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.semantics { selected = entry.selected }.combinedClickable(
            enabled = entry.enabled, onClick = onOpen, onLongClick = onLongPress
        ),
        color = if (entry.selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thumbnail != null) thumbnail()
            else Text(if (entry.directory) "Folder" else "File",
                style = MaterialTheme.typography.labelMedium)
            Text(entry.name, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.selected) Text("Selected", style = MaterialTheme.typography.labelSmall)
        }
    }
}

data class BreadcrumbUi(val id: String, val label: String)

/** Breadcrumb order comes from the provider path trail, never parse '/' from user-facing text. */
@Composable
fun VFilesBreadcrumbs(
    segments: List<BreadcrumbUi>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) Text(" / ", modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onNavigate(segment.id) }) {
                Text(segment.label, maxLines = 1)
            }
        }
    }
}

data class ContextActionUi(val id: String, val title: String, val enabled: Boolean = true,
    val primary: Boolean = false)

/** Selection/cut/copy/paste commands are hoisted. Safe horizontal scroll at 200% text. */
@Composable
fun VFilesContextActions(
    actions: List<ContextActionUi>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            if (action.primary) Button(
                onClick = { onAction(action.id) }, enabled = action.enabled
            ) { Text(action.title) }
            else OutlinedButton(
                onClick = { onAction(action.id) }, enabled = action.enabled
            ) { Text(action.title) }
        }
    }
}
