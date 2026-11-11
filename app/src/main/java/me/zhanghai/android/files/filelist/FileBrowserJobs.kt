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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
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
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private val etas = HashMap<Int, TaskEtaSmoother>()
    private val fractions = HashMap<Int, Float>()

    fun feed(jobs: List<FileJobProgress>) {
        val ids = jobs.mapTo(HashSet()) { it.id }
        trackers.keys.retainAll(ids)
        etas.keys.retainAll(ids)
        fractions.keys.retainAll(ids)
        for (job in jobs) {
            val time = job.updatedAtMillis ?: continue
            trackers.getOrPut(job.id) { FileJobSpeedTracker() }
                .update(time, job.processedBytes, job.processedFiles)
        }
    }

    fun snapshot(id: Int, nowMillis: Long): FileJobSpeedSnapshot =
        trackers[id]?.snapshot(nowMillis) ?: FileJobSpeedSnapshot()

    /**
     * The job's fraction, never below one already shown: totals that grow mid-job would otherwise
     * make the percentage tick backwards.
     */
    fun fraction(job: FileJobProgress): Float? {
        val fraction = job.fraction?.coerceIn(0f, 1f)
        if (fraction == null) {
            fractions.remove(job.id)
            return null
        }
        return maxOf(fraction, fractions[job.id] ?: 0f).also { fractions[job.id] = it }
    }

    /** Smoothed, rounded remaining time, shared so the list and details always agree. */
    fun etaSeconds(job: FileJobProgress, speed: FileJobSpeedSnapshot, nowMillis: Long): Long? {
        val raw = if (isTaskStalled(job, nowMillis) || isTaskWarmingUp(job, nowMillis)) {
            null
        } else {
            taskEtaSeconds(job, speed)
        }
        return etas.getOrPut(job.id) { TaskEtaSmoother() }.update(nowMillis, raw)
            ?.let(::roundTaskEtaSeconds)
    }
}

/**
 * Keeps a remaining-time estimate calm: between updates it counts down with the clock, drifts
 * toward a lower raw estimate, and only rises once the raw estimate is clearly higher, so it
 * neither jitters nor creeps up by a second at a time.
 */
internal class TaskEtaSmoother {
    private var anchorSeconds = 0.0
    private var anchorMillis = 0L
    private var hasAnchor = false
    private var lastMillis = Long.MIN_VALUE
    private var lastRaw: Long? = null
    private var last: Long? = null

    fun update(nowMillis: Long, rawSeconds: Long?): Long? {
        // Composition may ask several times for the same frame; only real changes may pull.
        if (nowMillis == lastMillis && rawSeconds == lastRaw) return last
        lastMillis = nowMillis
        lastRaw = rawSeconds
        if (rawSeconds == null) {
            hasAnchor = false
            last = null
            return null
        }
        val raw = rawSeconds.toDouble()
        val projected = anchorSeconds - (nowMillis - anchorMillis) / 1000.0
        val next = when {
            !hasAnchor || projected <= 0 || raw > projected * 2 || raw < projected / 2 -> raw
            raw < projected -> projected + (raw - projected) * PULL
            raw - projected > maxOf(projected * RISE_TOLERANCE, MIN_RISE_SECONDS) ->
                projected + (raw - projected) * PULL
            else -> projected
        }
        anchorSeconds = next
        anchorMillis = nowMillis
        hasAnchor = true
        return ceil(next).toLong().also { last = it }
    }

    private companion object {
        const val PULL = 0.3
        const val RISE_TOLERANCE = 0.15
        const val MIN_RISE_SECONDS = 3.0
    }
}

/**
 * Coarser steps for longer estimates; "4m 50s" that moves every ten seconds reads better than one
 * ticking every second, and nobody needs the seconds of a two-hour job.
 */
internal fun roundTaskEtaSeconds(seconds: Long): Long {
    val step = when {
        seconds <= 10 -> 1L
        seconds < 60 -> 5L
        seconds < 10 * 60 -> 10L
        seconds < 60 * 60 -> 60L
        seconds < 24 * 60 * 60 -> 5 * 60L
        else -> 60 * 60L
    }
    return ((seconds.coerceAtLeast(1) + step - 1) / step) * step
}

/** No progress update for a while: a stalled share, or the job waiting on a dialog. */
internal fun isTaskStalled(job: FileJobProgress, nowMillis: Long): Boolean {
    val updated = job.updatedAtMillis ?: return false
    return job.fraction != null && nowMillis - updated >= TaskStallMillis
}

/** The first seconds of a rate are mostly startup cost; an estimate then would be wild. */
private fun isTaskWarmingUp(job: FileJobProgress, nowMillis: Long): Boolean {
    val started = job.startedAtMillis ?: return false
    return nowMillis - started < TaskEtaWarmupMillis
}

private const val TaskStallMillis = 5_000L
private const val TaskEtaWarmupMillis = 3_000L

@Composable
internal fun TaskPill(jobs: List<FileJobProgress>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // An indeterminate job makes the aggregate indeterminate rather than a misleading average.
    val overall = aggregateFileJobFraction(jobs.map { TaskSpeedRegistry.fraction(it) })
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
            RollingText(label, MaterialTheme.typography.labelLargeEmphasized.tabular())
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
                    nowMillis = now,
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
    nowMillis: Long,
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
                val speed = speeds(job.id)
                val status = taskStatusLine(job, speed, durations, nowMillis)
                SegmentedListItem(
                    onClick = { onOpen(job.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, jobs.size),
                    colors = VFilesListDefaults.sheetSegmentedColors(),
                    leadingContent = {
                        TaskRing(TaskSpeedRegistry.fraction(job), job.kind.iconRes, 40.dp, 20.dp, 4.dp)
                    },
                    supportingContent = {
                        Text(
                            status, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
                        Text(job.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
        }
    }
}

/** "42% · 12 MB/s · 3m left", built from the same values the details sheet shows. */
@Composable
private fun taskStatusLine(
    job: FileJobProgress,
    speed: FileJobSpeedSnapshot,
    durations: TaskDurationFormats,
    nowMillis: Long
): String {
    val fraction = TaskSpeedRegistry.fraction(job) ?: return stringResource(R.string.task_preparing)
    val parts = mutableListOf(percentText(fraction))
    taskRateText(job, speed, nowMillis)?.let { parts += it }
    TaskSpeedRegistry.etaSeconds(job, speed, nowMillis)?.let {
        parts += stringResource(R.string.task_eta_format, formatTaskDuration(it, durations))
    }
    val separator = stringResource(R.string.task_separator_format, "", "")
    return parts.joinToString(separator)
}

/** Current rate in bytes, or in files for jobs that only count files; "Waiting…" when stalled. */
@Composable
private fun taskRateText(job: FileJobProgress, speed: FileJobSpeedSnapshot, nowMillis: Long): String? {
    if (job.fraction == null) return null
    if (isTaskStalled(job, nowMillis)) return stringResource(R.string.task_sheet_waiting)
    return if (job.isByteBased) {
        speed.bytesPerSecond?.let { speedText(LocalContext.current, it) }
    } else {
        speed.filesPerSecond?.let { filesRateText(it) }
    }
}

private val FileJobProgress.isByteBased: Boolean
    get() = totalBytes != null && processedBytes != null

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
    val fraction = TaskSpeedRegistry.fraction(job)
    val etaSeconds = TaskSpeedRegistry.etaSeconds(job, speed, nowMillis)
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
        TaskDetailsHeader(job)
        val byteBased = job.isByteBased
        val showSpeed = byteBased && fraction != null
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
            } else {
                taskRateText(job, speed, nowMillis)?.let {
                    add(stringResource(R.string.task_speed) to it)
                }
            }
            job.startedAtMillis?.let {
                add(
                    stringResource(R.string.task_elapsed) to
                        formatTaskDuration((nowMillis - it) / 1_000, durations)
                )
            }
        }
        val tileRows = tiles.chunked(2)
        val sections = 1 + (if (showSpeed) 1 else 0) + tileRows.size
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            TaskProgressSection(
                fraction, etaSeconds?.let { formatTaskDuration(it, durations) },
                sectionShape(0, sections)
            )
            if (showSpeed) {
                TaskSpeedSection(
                    speed, context, isTaskStalled(job, nowMillis), sectionShape(1, sections)
                )
            }
            tileRows.forEachIndexed { rowIndex, row ->
                val shape = sectionShape(sections - tileRows.size + rowIndex, sections)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier.height(IntrinsicSize.Min)
                ) {
                    row.forEachIndexed { i, (label, value) ->
                        TaskStatTile(
                            label, value, splitShape(shape, i, row.size),
                            Modifier.weight(1f).fillMaxHeight(), wide = row.size == 1
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

/**
 * Sheet header whose subtitle follows the file being processed. With many small files the name
 * changes on every update, so it is held for a moment and crossfaded instead of strobing.
 */
@Composable
private fun TaskDetailsHeader(job: FileJobProgress) {
    val subtitle = rememberHeld(job.currentFileName ?: job.detail, SubtitleHoldMillis)
    val motion = MaterialTheme.motionScheme
    // The header's bottom padding matches its inner spacing, so this reads as its own subtitle.
    Column {
        VFilesSheetHeader(icon = job.kind.iconRes, title = job.title)
        if (subtitle != null) {
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                contentAlignment = Alignment.Center,
                label = "taskSubtitle"
            ) { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TaskProgressSection(
    fraction: Float?,
    eta: String?,
    shape: Shape
) {
    // One focus stop: "25%, Time left, 4m 50s" plus the bar's progress range.
    TaskSection(shape, Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fraction != null) {
                RollingText(
                    percentText(fraction),
                    MaterialTheme.typography.displayMediumEmphasized.tabular(),
                    Modifier.alignBy(LastBaseline),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.alignBy(LastBaseline)
                ) {
                    TaskLabel(stringResource(R.string.task_time_left))
                    RollingText(
                        eta ?: NoValue,
                        MaterialTheme.typography.titleLargeEmphasized.tabular(),
                        increasing = false
                    )
                }
            } else {
                Text(
                    stringResource(R.string.task_preparing),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (fraction != null) {
            val animated by animateFloatAsState(
                fraction, WavyProgressIndicatorDefaults.ProgressAnimationSpec,
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
    stalled: Boolean,
    shape: Shape
) {
    TaskSection(shape) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f).alignBy(LastBaseline)) {
                TaskLabel(stringResource(R.string.task_speed))
                Text(
                    when {
                        stalled -> stringResource(R.string.task_sheet_waiting)
                        else -> speed.bytesPerSecond?.let { speedText(context, it) } ?: NoValue
                    },
                    style = MaterialTheme.typography.headlineSmallEmphasized.tabular(),
                    color = if (stalled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            speed.averageBytesPerSecond?.let {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.alignBy(LastBaseline)
                ) {
                    TaskLabel(stringResource(R.string.task_speed_average))
                    Text(
                        speedText(context, it),
                        style = MaterialTheme.typography.titleMediumEmphasized.tabular(),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val description = stringResource(R.string.task_speed_graph_description)
        TaskSpeedChart(
            speed.samples,
            Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }
        )
    }
}

@Composable
private fun TaskSection(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
    }
}

@Composable
private fun TaskStatTile(
    label: String,
    value: String,
    shape: Shape,
    modifier: Modifier,
    wide: Boolean = false
) {
    val valueStyle = MaterialTheme.typography.titleMediumEmphasized.tabular()
    Surface(
        shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        // A tile alone in its row reads as one line, rather than half an empty pair.
        if (wide) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TaskLabel(label, Modifier.weight(1f).alignBy(LastBaseline))
                Text(
                    value, style = valueStyle, textAlign = TextAlign.End, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.alignBy(LastBaseline)
                )
            }
            return@Surface
        }
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TaskLabel(label)
            Text(
                value,
                style = valueStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
private fun TaskLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Text whose changed digits roll vertically, for numbers that tick. Segments are keyed from the
 * end so the units stay put when the digit count changes. RTL layouts and RTL letters get plain
 * text: laying segments out side by side would bypass bidi reordering (e.g. where "%" goes).
 */
@Composable
private fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    increasing: Boolean = true
) {
    if (LocalLayoutDirection.current == LayoutDirection.Rtl ||
        text.any { it.isSurrogate() || it.isStrongRtl() }) {
        Text(text, modifier, color, style = style, maxLines = 1)
        return
    }
    val motion = MaterialTheme.motionScheme
    // Each digit rolls on its own; other runs stay whole so their kerning survives.
    val segments = buildList<String> {
        for (char in text) {
            if (char.isDigit() || isEmpty() || last().last().isDigit()) {
                add(char.toString())
            } else {
                add(removeAt(lastIndex) + char)
            }
        }
    }
    Layout(
        content = {
            // Baseline reference only, never placed.
            Text(text, color = color, style = style, maxLines = 1, softWrap = false)
            Row {
                segments.forEachIndexed { index, segment ->
                    key(segments.size - index) {
                        AnimatedContent(
                            targetState = segment,
                            modifier = Modifier.clipToBounds(),
                            transitionSpec = {
                                val sign = if (increasing) 1 else -1
                                slideInVertically(motion.fastSpatialSpec()) { sign * it }
                                    .togetherWith(
                                        slideOutVertically(motion.fastSpatialSpec()) { -sign * it }
                                    )
                                    .using(SizeTransform(clip = false) { _, _ -> motion.fastSpatialSpec() })
                            },
                            label = "rollingDigit"
                        ) {
                            Text(it, color = color, style = style, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = text }
    ) { measurables, constraints ->
        val reference = measurables[0].measure(constraints)
        val row = measurables[1].measure(constraints)
        // Sliding digits carry their baselines with them; report the resting ones instead.
        layout(
            row.width, reference.height,
            mapOf(
                FirstBaseline to reference[FirstBaseline],
                LastBaseline to reference[LastBaseline]
            )
        ) {
            row.place(0, 0)
        }
    }
}
private fun Char.isStrongRtl(): Boolean {
    val directionality = Character.getDirectionality(this)
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
        directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

/** [value], updated at most once per [holdMillis]; the newest value always lands eventually. */
@Composable
private fun <T> rememberHeld(value: T, holdMillis: Long): T {
    val latest by rememberUpdatedState(value)
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect {
            shown = it
            delay(holdMillis)
        }
    }
    return shown
}

private const val SubtitleHoldMillis = 1_000L

private const val NoValue = "\u2014"

@Composable
private fun filesRateText(filesPerSecond: Double): String {
    val format = remember(filesPerSecond < 10) {
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = if (filesPerSecond < 10) 1 else 0
        }
    }
    val rate = filesPerSecond.coerceAtLeast(0.0)
    return pluralStringResource(
        R.plurals.task_sheet_files_per_second, rate.roundToInt(), format.format(rate)
    )
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
