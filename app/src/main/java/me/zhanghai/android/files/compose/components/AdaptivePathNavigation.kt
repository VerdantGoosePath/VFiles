package me.zhanghai.android.files.compose.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Items represent storage/path destinations, not screen-global tabs or filters. */
data class StorageDestinationUi(
    val id: String,
    val title: String,
    val supporting: String? = null,
    val enabled: Boolean = true
)

/**
 * Responsive storage/path navigator. At >=600dp show a side pane; compact embeds scrollable
 * destination shortcuts above content, not a permanently open drawer obscuring files.
 * Host owner retains the file trail and Back handling. Width = current window, not device type.
 */
@Composable
fun VFilesAdaptivePathNavigation(
    destinations: List<StorageDestinationUi>,
    activeId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.width(220.dp).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    destinations.forEach { destination ->
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(destination.title)
                                    if (destination.supporting != null)
                                        Text(destination.supporting,
                                            style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            selected = destination.id == activeId,
                            onClick = { if (destination.enabled) onSelect(destination.id) }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) { content() }
            }
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    destinations.forEach { destination ->
                        TextButton(
                            onClick = { onSelect(destination.id) },
                            enabled = destination.enabled
                        ) {
                            Text(destination.title,
                                color = if (destination.id == activeId)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                content()
            }
        }
    }
}
