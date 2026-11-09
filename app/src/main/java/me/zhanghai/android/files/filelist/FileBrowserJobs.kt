@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.zhanghai.android.files.filelist

import android.os.SystemClock
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesBottomSheet
import me.zhanghai.android.files.compose.components.VFilesListDefaults
import me.zhanghai.android.files.compose.components.VFilesSheetColumn
import me.zhanghai.android.files.compose.components.VFilesSheetGroup
import me.zhanghai.android.files.compose.components.VFilesSheetHeader
import me.zhanghai.android.files.filejob.FileJobKind
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.filejob.FileJobSpeedSample
import me.zhanghai.android.files.filejob.FileJobSpeedSnapshot
import me.zhanghai.android.files.filejob.FileJobSpeedTracker
import me.zhanghai.android.files.filejob.TaskDurationFormats
import me.zhanghai.android.files.filejob.estimateRemainingSeconds
import me.zhanghai.android.files.filejob.formatTaskDuration
import me.zhanghai.android.files.filejob.formatTransferSpeed
import java.text.NumberFormat

/**
 * Multiple actual file jobs; the service owns IDs, cancellation, errors and notifications.
 * A compact tonal pill floats over the list; tapping it opens the task list, or the details of
 * the only task.
 */
@Composable
fun VFilesJobStatus(
    jobs: List<FileJobProgress>,
    onCancel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Sampled on every progress update, whether or not the sheet is open, so the speed graph
    // already has history when the user looks.
    remember(jobs) { TaskSpeedRegistry.feed(jobs) }
    // Keeps the last non-empty list so the pill and sheet can animate out with content.
    val retained = remember { RetainedJobs() }
    if (jobs.isNotEmpty()) retained.jobs = jobs
    val shown = retained.jobs
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var initialDetailsId by rememberSaveable { mutableStateOf<Int?>(null) }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = jobs.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Bottom) +
            scaleIn(motion.defaultSpatialSpec(), 0.6f, TransformOrigin(0.5f, 1f)) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Bottom) +
            scaleOut(motion.fastSpatialSpec(), 0.6f, TransformOrigin(0.5f, 1f)) +
            fadeOut(motion.fastEffectsSpec())
    ) {
        TaskPill(shown, onClick = {
            initialDetailsId = shown.singleOrNull()?.id
            sheetOpen = true
        })
    }
    if (sheetOpen) {
        TaskSheet(
            jobs = shown,
            finished = jobs.isEmpty(),
            initialDetailsId = initialDetailsId,
            onCancel = onCancel,
            onDismissRequest = { sheetOpen = false }
        )
    }
}

private class RetainedJobs {
    var jobs: List<FileJobProgress> = emptyList()
}

/** A total percentage is only truthful when *all* concurrent file jobs are measurable. */
internal fun aggregateFileJobFraction(fractions: List<Float?>): Float? {
    if (fractions.isEmpty() || fractions.any { it == null }) return null
    return fractions.requireNoNulls().map { it.coerceIn(0f, 1f) }.average().toFloat()
}

/** Speed history per job id. Main thread only; shared so recomposing elsewhere keeps history. */
internal object TaskSpeedRegistry {
    private val trackers = HashMap<Int, FileJobSpeedTracker>()

    fun feed(jobs: List<FileJobProgress>) {
        val ids = jobs.mapTo(HashSet()) { it.id }
        trackers.keys.retainAll(ids)
        for (job in jobs) {
            val time = job.updatedAtMillis ?: continue
            trackers.getOrPut(job.id) { FileJobSpeedTracker() }
                .update(time, job.processedBytes, job.processedFiles)
        }
    }

    fun snapshot(id: Int, nowMillis: Long): FileJobSpeedSnapshot =
        trackers[id]?.snapshot(nowMillis) ?: FileJobSpeedSnapshot()
}

@Composable
internal fun TaskPill(jobs: List<FileJobProgress>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // An indeterminate job makes the aggregate indeterminate rather than a misleading average.
    val overall = aggregateFileJobFraction(jobs.map { it.fraction })
    val single = jobs.singleOrNull()
    val name = if (single != null) {
        stringResource(single.kind.labelRes)
    } else {
        pluralStringResource(R.plurals.task_count, jobs.size, jobs.size)
    }
    val label = if (overall != null) {
        stringResource(R.string.task_separator_format, name, percentText(overall))
    } else {
        name
    }
    val description = pluralStringResource(R.plurals.file_browser_jobs_running, jobs.size, jobs.size)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                .heightIn(min = 48.dp)
                .padding(start = 8.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskRing(
                fraction = overall,
                icon = single?.kind?.iconRes ?: R.drawable.task_icon_tasks,
                size = 32.dp,
                iconSize = 16.dp,
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
            )
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized.tabular(), maxLines = 1)
        }
    }
}

/** Wavy circular progress around the task's icon; animates between progress updates. */
@Composable
private fun TaskRing(
    fraction: Float?,
    icon: Int,
    size: Dp,
    iconSize: Dp,
    strokeWidth: Dp,
    trackColor: androidx.compose.ui.graphics.Color = WavyProgressIndicatorDefaults.trackColor
) {
    val stroke = with(LocalDensity.current) { Stroke(strokeWidth.toPx(), cap = StrokeCap.Round) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (fraction == null) {
            CircularWavyProgressIndicator(
                Modifier.size(size), trackColor = trackColor, stroke = stroke, trackStroke = stroke
            )
        } else {
            val animated by animateFloatAsState(
                fraction.coerceIn(0f, 1f), WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "taskProgress"
            )
            CircularWavyProgressIndicator(
                progress = { animated }, modifier = Modifier.size(size), trackColor = trackColor,
                stroke = stroke, trackStroke = stroke
            )
        }
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun TaskSheet(
    jobs: List<FileJobProgress>,
    finished: Boolean,
    initialDetailsId: Int?,
    onCancel: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    // remember, not rememberSaveable: a new opening must win over any restored sheet state.
    var detailsId by remember(initialDetailsId) { mutableStateOf(initialDetailsId) }
    val now by rememberElapsedRealtime()
    VFilesBottomSheet(onDismissRequest = onDismissRequest) { controller ->
        LaunchedEffect(finished) { if (finished) controller.dismiss() }
        val details = jobs.firstOrNull { it.id == detailsId }
        val canGoBack = details != null && jobs.size > 1
        BackHandler(enabled = canGoBack) { detailsId = null }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = details?.id,
            transitionSpec = {
                val forward = targetState != null
                val sign = if (forward) 1 else -1
                (slideInHorizontally(motion.defaultSpatialSpec()) { sign * it / 4 } +
                    fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motion.defaultSpatialSpec()) { -sign * it / 4 } +
                            fadeOut(motion.fastEffectsSpec())
                    )
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "taskSheet"
        ) { id ->
            val job = jobs.firstOrNull { it.id == id }
            if (job == null) {
                TaskListContent(
                    jobs = jobs,
                    speeds = { TaskSpeedRegistry.snapshot(it, now) },
                    onOpen = { detailsId = it },
                    onCancel = onCancel
                )
            } else {
                TaskDetailsContent(
                    job = job,
                    speed = TaskSpeedRegistry.snapshot(job.id, now),
                    nowMillis = now,
                    onBack = if (jobs.size > 1) ({ detailsId = null }) else null,
                    onCancel = { onCancel(job.id) }
                )
            }
        }
    }
}

@Composable
private fun rememberElapsedRealtime() =
    produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }

@Composable
internal fun TaskListContent(
    jobs: List<FileJobProgress>,
    speeds: (Int) -> FileJobSpeedSnapshot,
    onOpen: (Int) -> Unit,
    onCancel: (Int) -> Unit
) {
    VFilesSheetColumn {
        VFilesSheetHeader(
            icon = R.drawable.task_icon_tasks,
            title = stringResource(R.string.file_job_progress_title),
            subtitle = pluralStringResource(R.plurals.file_browser_jobs_running, jobs.size, jobs.size)
        )
        VFilesSheetGroup {
            val durations = rememberTaskDurationFormats()
            jobs.forEachIndexed { index, job ->
                val status = taskStatusLine(job, speeds(job.id), durations)
                SegmentedListItem(
                    onClick = { onOpen(job.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, jobs.size),
                    colors = VFilesListDefaults.sheetSegmentedColors(),
                    leadingContent = {
                        TaskRing(job.fraction, job.kind.iconRes, 40.dp, 20.dp, 4.dp)
                    },
                    supportingContent = {
                        Text(
                            status, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.tabular()
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onCancel(job.id) }) {
                            Icon(
                                painterResource(R.drawable.task_icon_cancel),
                                contentDescription = stringResource(R.string.task_cancel)
                            )
                        }
                    },
                    content = {
                        Text(job.title, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                    }
                )
            }
        }
    }
}

@Composable
private fun taskStatusLine(
    job: FileJobProgress,
    speed: FileJobSpeedSnapshot,
    durations: TaskDurationFormats
): String {
    val context = LocalContext.current
    val parts = mutableListOf<String>()
    parts += job.fraction?.let { percentText(it) } ?: stringResource(R.string.task_preparing)
    if (job.fraction != null && job.totalBytes != null) {
        speed.bytesPerSecond?.let { parts += speedText(context, it) }
    }
    taskEtaSeconds(job, speed)?.let {
        parts += stringResource(R.string.task_eta_format, formatTaskDuration(it, durations))
    }
    val separator = stringResource(R.string.task_separator_format, "", "")
    return parts.joinToString(separator)
}

/** Remaining time from bytes when the job reports them, otherwise from the file rate. */
internal fun taskEtaSeconds(job: FileJobProgress, speed: FileJobSpeedSnapshot): Long? {
    if (job.fraction == null) return null
    val totalBytes = job.totalBytes
    val processedBytes = job.processedBytes
    if (totalBytes != null && processedBytes != null) {
        return estimateRemainingSeconds(
            (totalBytes - processedBytes).coerceAtLeast(0), speed.bytesPerSecond
        )
    }
    val totalFiles = job.totalFiles ?: return null
    val processedFiles = job.processedFiles ?: return null
    return estimateRemainingSeconds(
        (totalFiles - processedFiles).coerceAtLeast(0).toLong(), speed.filesPerSecond
    )
}

@Composable
internal fun TaskDetailsContent(
    job: FileJobProgress,
    speed: FileJobSpeedSnapshot,
    nowMillis: Long,
    onBack: (() -> Unit)?,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val durations = rememberTaskDurationFormats()
    val numbers = remember { NumberFormat.getIntegerInstance() }
    VFilesSheetColumn {
        if (onBack != null) {
            TextButton(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painterResource(R.drawable.task_icon_back), contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.task_all_tasks))
            }
        }
        VFilesSheetHeader(
            icon = job.kind.iconRes,
            title = job.title,
            subtitle = job.currentFileName ?: job.detail
        )
        val byteBased = job.totalBytes != null && job.processedBytes != null
        val showSpeed = byteBased && job.fraction != null
        val tiles = buildList {
            val totalFiles = job.totalFiles
            val processedFiles = job.processedFiles
            if (totalFiles != null && processedFiles != null && totalFiles > 1) {
                add(
                    stringResource(R.string.task_files) to pluralStringResource(
                        R.plurals.task_files_progress_format, totalFiles,
                        numbers.format(processedFiles), numbers.format(totalFiles)
                    )
                )
            }
            if (byteBased) {
                add(
                    stringResource(R.string.task_size) to stringResource(
                        R.string.task_size_progress_format,
                        Formatter.formatShortFileSize(context, job.processedBytes!!),
                        Formatter.formatShortFileSize(context, job.totalBytes!!)
                    )
                )
            }
            job.startedAtMillis?.let {
                add(
                    stringResource(R.string.task_elapsed) to
                        formatTaskDuration((nowMillis - it) / 1_000, durations)
                )
            }
            if (showSpeed) {
                speed.averageBytesPerSecond?.let {
                    add(stringResource(R.string.task_speed_average) to speedText(context, it))
                }
            }
        }
        val tileRows = tiles.chunked(2)
        val sections = 1 + (if (showSpeed) 1 else 0) + tileRows.size
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            TaskProgressSection(job, taskEtaSeconds(job, speed), durations, sectionShape(0, sections))
            if (showSpeed) {
                TaskSpeedSection(speed, context, sectionShape(1, sections))
            }
            tileRows.forEachIndexed { rowIndex, row ->
                val shape = sectionShape(sections - tileRows.size + rowIndex, sections)
                Row(horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    row.forEachIndexed { i, (label, value) ->
                        TaskStatTile(
                            label, value, splitShape(shape, i, row.size), Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            val iconSize = ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)
            Icon(
                painterResource(R.drawable.task_icon_cancel), contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                stringResource(R.string.task_cancel),
                style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)
            )
        }
    }
}

@Composable
private fun TaskProgressSection(
    job: FileJobProgress,
    etaSeconds: Long?,
    durations: TaskDurationFormats,
    shape: Shape
) {
    TaskSection(shape) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                job.fraction?.let { percentText(it) } ?: stringResource(R.string.task_preparing),
                style = if (job.fraction != null) {
                    MaterialTheme.typography.displayMediumEmphasized
                } else {
                    MaterialTheme.typography.headlineSmallEmphasized
                }.tabular(),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            if (etaSeconds != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.task_time_left),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatTaskDuration(etaSeconds, durations),
                        style = MaterialTheme.typography.titleLargeEmphasized.tabular()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val fraction = job.fraction
        if (fraction != null) {
            val animated by animateFloatAsState(
                fraction.coerceIn(0f, 1f), WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "taskDetailsProgress"
            )
            LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TaskSpeedSection(
    speed: FileJobSpeedSnapshot,
    context: android.content.Context,
    shape: Shape
) {
    TaskSection(shape) {
        Text(
            stringResource(R.string.task_speed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            speed.bytesPerSecond?.let { speedText(context, it) } ?: "\u2014",
            style = MaterialTheme.typography.headlineSmallEmphasized.tabular()
        )
        Spacer(Modifier.height(12.dp))
        val description = stringResource(R.string.task_speed_graph_description)
        TaskSpeedGraph(
            speed.samples,
            Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription = description }
        )
    }
}

@Composable
private fun TaskSection(shape: Shape, content: @Composable () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
    }
}

@Composable
private fun TaskStatTile(label: String, value: String, shape: Shape, modifier: Modifier) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMediumEmphasized.tabular(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Rolling speed chart. The time axis is anchored to the newest sample and glides to each new
 * one, so the line scrolls smoothly at the notification rate instead of jumping.
 */
@Composable
private fun TaskSpeedGraph(samples: List<FileJobSpeedSample>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val dotRing = MaterialTheme.colorScheme.surfaceContainerHigh
    val latest = samples.lastOrNull()?.timeMillis ?: 0L
    val anchor = remember { Animatable(latest.toFloat()) }
    LaunchedEffect(latest) {
        if (latest.toFloat() - anchor.value > GraphWindowMillis) {
            anchor.snapTo(latest.toFloat())
        } else {
            anchor.animateTo(latest.toFloat(), tween(GraphGlideMillis, easing = LinearEasing))
        }
    }
    val peak = (samples.maxOfOrNull { it.bytesPerSecond } ?: 0f).coerceAtLeast(1f)
    val scale by animateFloatAsState(
        peak * 1.15f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "speedScale"
    )
    Canvas(modifier) {
        val inset = 6.dp.toPx()
        val width = size.width - inset * 2
        val height = size.height - inset * 2
        val bottom = inset + height
        drawLine(
            baseline, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx(),
            cap = StrokeCap.Round
        )
        if (samples.size < 2) return@Canvas
        val end = anchor.value
        val points = samples.map {
            val x = inset + width * (1f - (end - it.timeMillis) / GraphWindowMillis)
            val y = bottom - height * (it.bytesPerSecond / scale).coerceIn(0f, 1f)
            Offset(x, y)
        }
        val line = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val mid = (previous.x + current.x) / 2
                cubicTo(mid, previous.y, mid, current.y, current.x, current.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, bottom)
            lineTo(points.first().x, bottom)
            close()
        }
        clipRect(left = 0f, right = size.width) {
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)),
                    startY = inset, endY = bottom
                )
            )
            drawPath(
                line, color,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        val last = points.last()
        drawCircle(dotRing, 6.dp.toPx(), last)
        drawCircle(color, 4.dp.toPx(), last)
    }
}

private val GraphWindowMillis = FileJobSpeedTracker.DEFAULT_WINDOW_MILLIS.toFloat()
private const val GraphGlideMillis = 500

/** Corner set for one of [count] stacked sections, matching the app's segmented groups. */
@Composable
private fun sectionShape(index: Int, count: Int): Shape =
    ListItemDefaults.segmentedShapes(index, count).shape

/** Splits a row's shape so side-by-side tiles share small inner corners. */
@Composable
private fun splitShape(shape: Shape, index: Int, count: Int): Shape {
    if (count == 1 || shape !is CornerBasedShape) return shape
    val inner = (ListItemDefaults.segmentedShapes(1, 3).shape as? CornerBasedShape)
        ?: return shape
    return if (index == 0) {
        shape.copy(topEnd = inner.topEnd, bottomEnd = inner.bottomEnd)
    } else {
        shape.copy(topStart = inner.topStart, bottomStart = inner.bottomStart)
    }
}

@Composable
private fun rememberTaskDurationFormats(): TaskDurationFormats {
    val seconds = stringResource(R.string.task_duration_seconds_format)
    val minutes = stringResource(R.string.task_duration_minutes_format)
    val minutesSeconds = stringResource(R.string.task_duration_minutes_seconds_format)
    val hours = stringResource(R.string.task_duration_hours_format)
    val hoursMinutes = stringResource(R.string.task_duration_hours_minutes_format)
    val days = stringResource(R.string.task_duration_days_format)
    val daysHours = stringResource(R.string.task_duration_days_hours_format)
    return remember(seconds, minutes, minutesSeconds, hours, hoursMinutes, days, daysHours) {
        TaskDurationFormats(seconds, minutes, minutesSeconds, hours, hoursMinutes, days, daysHours)
    }
}

@Composable
private fun percentText(fraction: Float): String =
    stringResource(R.string.task_percent_format, (fraction.coerceIn(0f, 1f) * 100).toInt())

private fun speedText(context: android.content.Context, bytesPerSecond: Double): String =
    formatTransferSpeed(
        bytesPerSecond, { Formatter.formatShortFileSize(context, it) },
        context.getString(R.string.task_speed_format)
    )

/** Digits keep their width so ticking percentages and speeds do not jitter. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val FileJobKind.iconRes: Int
    get() = when (this) {
        FileJobKind.COPY -> R.drawable.task_icon_copy
        FileJobKind.MOVE -> R.drawable.task_icon_move
        FileJobKind.EXTRACT -> R.drawable.task_icon_extract
        FileJobKind.DELETE -> R.drawable.task_icon_delete
        FileJobKind.ARCHIVE -> R.drawable.task_icon_archive
        FileJobKind.WRITE -> R.drawable.task_icon_save
        FileJobKind.OTHER -> R.drawable.task_icon_tasks
    }

private val FileJobKind.labelRes: Int
    get() = when (this) {
        FileJobKind.COPY -> R.string.task_kind_copy
        FileJobKind.MOVE -> R.string.task_kind_move
        FileJobKind.EXTRACT -> R.string.task_kind_extract
        FileJobKind.DELETE -> R.string.task_kind_delete
        FileJobKind.ARCHIVE -> R.string.task_kind_archive
        FileJobKind.WRITE -> R.string.task_kind_write
        FileJobKind.OTHER -> R.string.task_kind_other
    }
