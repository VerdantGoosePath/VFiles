package me.zhanghai.android.files.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Data-only preference row. The owning screen reads/writes SettingLiveData, not this component. */
data class PreferenceRowUi(
    val key: String,
    val title: String,
    val supporting: String? = null,
    val currentValue: String? = null,
    val enabled: Boolean = true
)

/**
 * Native M3E segmented group (Material3 1.5 RC).
 * Hoist state with [rows]; avoid storing settings in remember/rememberSaveable.
 * Row semantics, focus, press shapes and keyboard activation belong to the native component.
 */
@Composable
fun VFilesPreferenceGroup(
    title: String,
    rows: List<PreferenceRowUi>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                SegmentedListItem(
                    onClick = { onSelect(row.key) },
                    enabled = row.enabled,
                    shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                    content = { Text(row.title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = row.supporting?.let { subtitle ->
                        { Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
                    },
                    trailingContent = row.currentValue?.let { value ->
                        { Text(value, style = MaterialTheme.typography.labelLarge) }
                    }
                )
            }
        }
    }
}
