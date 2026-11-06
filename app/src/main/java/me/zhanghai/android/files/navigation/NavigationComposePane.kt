package me.zhanghai.android.files.navigation

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A screen's storage roots are not three app tabs; keep the original navigation hierarchy. */
internal data class NavigationEntryUi(
    val item: NavigationItem?,
    val title: String = "",
    val subtitle: String? = null
)

/**
 * The subtitles for local/removable volumes can query free disk space. Resolve them off-main
 * and refresh on open, volume change, and after file jobs. Actions call the original NavigationItem.
 */
@Composable
internal fun VFilesNavigationPane(
    items: List<NavigationItem?>,
    currentPath: Path?,
    revision: Int,
    listener: NavigationItem.Listener,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entries by produceState(
        initialValue = emptyList<NavigationEntryUi>(), items, revision
    ) {
        value = withContext(Dispatchers.IO) {
            items.map { item ->
                if (item == null) NavigationEntryUi(null)
                else NavigationEntryUi(
                    item,
                    title = item.getTitle(context),
                    subtitle = item.getSubtitle(context)
                )
            }
        }
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                entries,
                key = { index, entry -> entry.item?.id?.toString() ?: "divider-" + index },
                contentType = { _, entry -> when {
                    entry.item == null -> "divider"
                    entry.item.isHeader -> "header"
                    else -> "destination"
                } }
            ) { _, entry ->
                val item = entry.item
                when {
                    item == null -> HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    item.isHeader -> Text(
                        text = entry.title,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {
                        NavigationDestination(entry, item, currentPath, listener, context)
                    }
                }
            }
        }
    }
}

/** Tappable rows preserve path, storage and menu item actions, plus long-press edit callbacks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationDestination(
    entry: NavigationEntryUi,
    item: NavigationItem,
    currentPath: Path?,
    listener: NavigationItem.Listener,
    context: Context
) {
    // Existing isChecked() consults currentPath and is provider-aware for all item variants.
    // NavigationFragment observes the actual current path; it triggers recomposition on changes.
    val checked = currentPath != null && item.isChecked(listener)
    val background = if (checked) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { selected = checked }
            .combinedClickable(
                onClick = { item.onClick(listener) },
                onLongClick = { item.onLongClick(listener) }
            ),
        shape = MaterialTheme.shapes.large,
        color = background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (item.isIndented) 32.dp else 12.dp,
                end = 12.dp, top = 10.dp, bottom = 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { ImageView(it) },
                modifier = Modifier.size(24.dp),
                update = { image ->
                    image.setImageDrawable(item.getIcon(context))
                    image.contentDescription = null
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title, style = MaterialTheme.typography.titleMedium,
                    color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!entry.subtitle.isNullOrBlank()) {
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
