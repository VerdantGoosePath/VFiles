package me.zhanghai.android.files.filelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.FileJobProgress

/** Multiple actual file jobs; the service owns IDs, cancellation, errors and notifications. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VFilesJobStatus(
    jobs: List<FileJobProgress>,
    onCancel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (jobs.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val known = jobs.mapNotNull { it.fraction }
    val overall = known.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    Column(modifier) {
        Card(onClick = { expanded = true }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (overall == null) {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        ((overall * 100).toInt()).toString() + "%",
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
                Text(
                    stringResource(R.string.file_job_progress_title) + " · " + jobs.size,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 248.dp, max = 360.dp)
        ) {
            jobs.forEach { job ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = 16.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!job.detail.isNullOrBlank()) {
                        Text(
                            job.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (job.fraction != null) {
                        LinearProgressIndicator(
                            progress = { job.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            ((job.fraction * 100).toInt()).toString() + "%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LoadingIndicator(Modifier.size(24.dp))
                            Text(stringResource(R.string.loading))
                        }
                    }
                    TextButton(onClick = { onCancel(job.id) }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}
