@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.compose.components.VFilesListDefaults

/**
 * Multiple actual file jobs; the service owns IDs, cancellation, errors and notifications.
 * A compact tonal pill floats over the list; the per-job breakdown opens in a bottom sheet.
 */
@Composable
fun VFilesJobStatus(
    jobs: List<FileJobProgress>,
    onCancel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(jobs.isEmpty()) { if (jobs.isEmpty()) sheetOpen = false }
    if (jobs.isEmpty()) return
    // Never report a misleading 50% total when another concurrent job has no
    // measurable fraction. An indeterminate job makes the aggregate indeterminate.
    val overall = aggregateFileJobFraction(jobs.map { it.fraction })
    Surface(
        onClick = { sheetOpen = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (overall == null) {
                CircularWavyProgressIndicator(Modifier.size(32.dp))
            } else {
                CircularWavyProgressIndicator(progress = { overall }, modifier = Modifier.size(32.dp))
            }
            Column {
                Text(
                    pluralStringResource(R.plurals.file_browser_jobs_running, jobs.size, jobs.size),
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
                if (overall != null) {
                    Text(
                        ((overall * 100).toInt()).toString() + "%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                Text(
                    stringResource(R.string.file_job_progress_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
                        .semantics { heading() }
                )
                jobs.forEachIndexed { index, job ->
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index, jobs.size),
                        colors = VFilesListDefaults.sheetSegmentedColors(),
                        supportingContent = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (!job.detail.isNullOrBlank()) {
                                    Text(job.detail, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (job.fraction != null) {
                                    LinearWavyProgressIndicator(
                                        progress = { job.fraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { onCancel(job.id) },
                                shapes = ButtonDefaults.shapes()
                            ) { Text(stringResource(android.R.string.cancel)) }
                        },
                        content = {
                            Text(job.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    )
                }
            }
        }
    }
}

/** A total percentage is only truthful when *all* concurrent file jobs are measurable. */
internal fun aggregateFileJobFraction(fractions: List<Float?>): Float? {
    if (fractions.isEmpty() || fractions.any { it == null }) return null
    return fractions.requireNoNulls().map { it.coerceIn(0f, 1f) }.average().toFloat()
}
