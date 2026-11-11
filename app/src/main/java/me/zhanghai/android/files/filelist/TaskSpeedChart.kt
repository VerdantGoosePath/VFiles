package me.zhanghai.android.files.filelist

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.systemAnimationsDisabled
import me.zhanghai.android.files.filejob.FileJobRateSmoother
import me.zhanghai.android.files.filejob.FileJobSpeedSample
import me.zhanghai.android.files.filejob.FileJobSpeedTracker
import me.zhanghai.android.files.filejob.formatTransferSpeed
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Rolling transfer-speed chart. Time comes from the frame clock, so the curve scrolls at a
 * constant speed however irregularly progress updates arrive; the right edge trails real time by
 * [LatencyMillis] so the newest segment is usually already known instead of bending as samples
 * land. Between and after samples the curve follows the stall decay of
 * [FileJobRateSmoother.rateAt], so a stalled transfer visibly falls toward zero.
 *
 * Time flows left to right in every layout direction, like the platform's data usage charts;
 * only the scale label moves to the start side in RTL.
 */
@Composable
internal fun TaskSpeedChart(
    samples: List<FileJobSpeedSample>,
    modifier: Modifier = Modifier,
    windowMillis: Long = FileJobSpeedTracker.DEFAULT_WINDOW_MILLIS,
    /** [SystemClock.elapsedRealtime], the time base of [FileJobSpeedSample.timeMillis]. */
    clock: () -> Long = SystemClock::elapsedRealtime,
    reduceMotion: Boolean = systemAnimationsDisabled(LocalContext.current),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    val series = remember(samples, windowMillis) { SpeedSeries.of(samples, windowMillis) }
    val viewport = remember { SpeedViewport(clock()) }
    val currentSeries by rememberUpdatedState(series)
    val currentClock by rememberUpdatedState(clock)
    LaunchedEffect(viewport, reduceMotion, series != null, windowMillis) {
        if (currentSeries == null) return@LaunchedEffect
        if (reduceMotion) {
            // Steps once a second, like the speed text, instead of scrolling.
            while (true) {
                val now = currentClock()
                viewport.nowMillis = now
                currentSeries?.let { viewport.updateScale(it, now, windowMillis, Long.MAX_VALUE) }
                delay(1_000 - now.mod(1_000L))
            }
        }
        var offset = 0L
        var lastFrame = Long.MIN_VALUE
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                // Vsync-aligned frame times scroll evenly; re-anchor to the sample clock after a
                // pause, since the frame clock does not count deep sleep.
                val elapsed = if (lastFrame == Long.MIN_VALUE) Long.MAX_VALUE else frame - lastFrame
                if (elapsed > RebaseGapMillis) offset = currentClock() - frame
                lastFrame = frame
                val now = frame + offset
                viewport.nowMillis = now
                currentSeries?.let { viewport.updateScale(it, now, windowMillis, elapsed) }
            }
        }
    }
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer, labelStyle, context) {
        LabelCache(measurer, labelStyle) {
            formatTransferSpeed(
                it.toDouble(), { bytes -> Formatter.formatShortFileSize(context, bytes) },
                context.getString(R.string.task_speed_format)
            )
        }
    }
    val line = remember { Path() }
    val fill = remember { Path() }
    Spacer(
        modifier.drawWithCache {
            val stroke = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val gridWidth = 1.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
            val labelHeight = (if (labelStyle.lineHeight.isSpecified) labelStyle.lineHeight else 16.sp)
                .toPx()
            val frame = ChartFrame(
                left = 0f,
                right = size.width - DotRadius.toPx(),
                top = labelHeight + LabelGap.toPx() * 2,
                bottom = size.height - stroke.width / 2
            )
            val fillBrush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f)),
                startY = frame.top, endY = frame.bottom
            )
            val fadeWidth = size.width * FadeFraction
            val fadeBrush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.Black), startX = 0f, endX = fadeWidth
            )
            val labelledGrid = GridStyle(gridColor, gridWidth, dash, labelColor, labelled = true)
            val plainGrid = GridStyle(gridColor, gridWidth, dash, labelColor, labelled = false)
            val layerPaint = Paint()
            val layerBounds = Rect(Offset.Zero, size)
            onDrawBehind {
                drawLine(
                    gridColor, Offset(0f, frame.bottom), Offset(size.width, frame.bottom),
                    gridWidth
                )
                val data = series
                if (data == null) {
                    drawGrid(frame, frame.top, gridColor.copy(alpha = 0.6f), gridWidth, dash)
                    return@onDrawBehind
                }
                val time = chartTime(data, viewport.nowMillis, windowMillis)
                val resolved = viewport.target > 0
                val peak = if (resolved) 0f else data.peak(time.start, time.end)
                val target = if (resolved) viewport.target else nextScaleTarget(0f, peak)
                val scale = if (resolved) viewport.scale else target
                val previous = if (resolved) viewport.previous else target
                val hasSignal = if (resolved) viewport.hasSignal else peak > SignalFloor
                val progress = scaleProgress(scale, previous, target)
                // Gridlines are pinned to their values, so they glide with the scale; the
                // outgoing and incoming tops cross-fade as it travels.
                val style = if (hasSignal) labelledGrid else plainGrid
                drawScaleLine(frame, target, scale, progress, style, labels)
                if (previous != target) {
                    drawScaleLine(frame, previous, scale, 1 - progress, style, labels)
                }
                val tip = data.trace(line, fill, time, frame, scale)
                if (tip != null) {
                    clipRect(0f, 0f, size.width, size.height) {
                        drawContext.canvas.saveLayer(layerBounds, layerPaint)
                        drawPath(fill, fillBrush)
                        drawPath(line, lineColor, style = stroke)
                        drawRect(fadeBrush, Offset.Zero, Size(fadeWidth, size.height), blendMode = BlendMode.DstIn)
                        drawContext.canvas.restore()
                    }
                    drawCircle(containerColor, DotRadius.toPx(), tip)
                    drawCircle(lineColor, DotRadius.toPx() - DotRing.toPx(), tip)
                }
            }
        }
    )
}

private class GridStyle(
    val color: Color,
    val width: Float,
    val dash: PathEffect,
    val labelColor: Color,
    val labelled: Boolean
)

private fun DrawScope.drawScaleLine(
    frame: ChartFrame,
    value: Float,
    scale: Float,
    alpha: Float,
    style: GridStyle,
    labels: LabelCache
) {
    if (alpha <= 0.01f) return
    val y = frame.yOf(value, scale)
    drawGrid(frame, y, style.color.copy(alpha = style.color.alpha * alpha), style.width, style.dash)
    if (!style.labelled) return
    val layout = labels[value]
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - layout.size.width else 0f
    drawText(
        layout, style.labelColor, Offset(x, y - LabelGap.toPx() - layout.size.height),
        alpha = alpha
    )
}

private fun DrawScope.drawGrid(
    frame: ChartFrame,
    top: Float,
    color: Color,
    width: Float,
    dash: PathEffect
) {
    val mid = (top + frame.bottom) / 2
    drawLine(color, Offset(0f, top), Offset(size.width, top), width, pathEffect = dash)
    drawLine(color.copy(alpha = color.alpha * 0.6f), Offset(0f, mid), Offset(size.width, mid), width, pathEffect = dash)
}

internal class ChartFrame(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun yOf(value: Float, scale: Float): Float = bottom - value / scale * (bottom - top)
}

/** The visible time range: [end] trails now by [LatencyMillis]; it never shows the future. */
internal class ChartTime(val start: Long, val end: Long) {
    val span: Long get() = end - start
}

/**
 * The window grows with the job, from [MinSpanMillis] to the full [windowMillis], so a young job
 * fills the width instead of drawing a stub at the right edge.
 */
internal fun chartTime(series: SpeedSeries, nowMillis: Long, windowMillis: Long): ChartTime {
    val end = maxOf(nowMillis, series.lastSampleMillis) - LatencyMillis
    val span = (end - series.originMillis).coerceIn(MinSpanMillis, windowMillis.coerceAtLeast(MinSpanMillis))
    return ChartTime(end - span, end)
}

/** How far the displayed scale has travelled from [previous] to [target], in log space. */
internal fun scaleProgress(scale: Float, previous: Float, target: Float): Float {
    if (previous == target || previous <= 0 || target <= 0) return 1f
    return (ln(scale / previous) / ln(target / previous)).coerceIn(0f, 1f)
}

/** Draw-phase state; written once per frame by the chart's frame loop. */
private class SpeedViewport(initialNow: Long) {
    var nowMillis by mutableLongStateOf(initialNow)
    var scale by mutableFloatStateOf(0f)
    var target by mutableFloatStateOf(0f)
    var previous by mutableFloatStateOf(0f)
    var hasSignal by mutableStateOf(false)

    fun updateScale(series: SpeedSeries, nowMillis: Long, windowMillis: Long, elapsedMillis: Long) {
        val time = chartTime(series, nowMillis, windowMillis)
        val peak = series.peak(time.start, time.end)
        hasSignal = peak > SignalFloor
        val next = nextScaleTarget(target, peak)
        if (next != target) {
            previous = if (target > 0) target else next
            target = next
        }
        if (scale <= 0 || elapsedMillis == Long.MAX_VALUE) {
            scale = target
            previous = target
            return
        }
        // Exponential approach in log space: equal ratios take equal time, up or down.
        val k = 1 - exp(-elapsedMillis.toFloat() / ScaleTimeConstantMillis)
        val eased = scale * (target / scale).pow(k)
        scale = if (abs(ln(eased / target)) < 1e-3f) target else eased
        if (scale == target) previous = target
    }
}

private class LabelCache(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val format: (Float) -> String
) {
    private val values = floatArrayOf(Float.NaN, Float.NaN)
    private val layouts = arrayOfNulls<TextLayoutResult>(2)
    private var next = 0

    operator fun get(value: Float): TextLayoutResult {
        for (i in values.indices) {
            if (values[i] == value) return layouts[i]!!
        }
        val layout = measurer.measure(format(value), style.copy(textDirection = TextDirection.Content))
        values[next] = value
        layouts[next] = layout
        next = 1 - next
        return layout
    }
}

/**
 * Speed history resampled onto a fixed [stepMillis] grid with Fritsch–Carlson tangents, so the
 * curve is smooth but never overshoots below zero or above a real peak. Past the last sample the
 * series continues with the smoother's stall decay down to zero.
 */
internal class SpeedSeries(
    val startMillis: Long,
    val stepMillis: Long,
    val values: FloatArray,
    val slopes: FloatArray,
    /** Where the history begins, for the expanding window; stable while samples are trimmed. */
    val originMillis: Long,
    val lastSampleMillis: Long
) {
    val endMillis: Long get() = startMillis + (values.size - 1) * stepMillis

    fun valueAt(timeMillis: Long): Float {
        if (timeMillis <= startMillis) return values.first()
        if (timeMillis >= endMillis) return values.last()
        val position = (timeMillis - startMillis).toFloat() / stepMillis
        val k = floor(position).toInt().coerceAtMost(values.size - 2)
        return hermite(values[k], values[k + 1], slopes[k], slopes[k + 1], position - k)
    }

    /** The highest point between the times; monotone segments peak at their ends. */
    fun peak(fromMillis: Long, toMillis: Long): Float {
        var peak = maxOf(valueAt(fromMillis), valueAt(toMillis))
        val first = ceilDiv(fromMillis - startMillis, stepMillis).coerceAtLeast(0)
        val last = Math.floorDiv(toMillis - startMillis, stepMillis).coerceAtMost(values.size - 1L)
        for (k in first..last) peak = maxOf(peak, values[k.toInt()])
        return peak
    }

    /**
     * Rebuilds [line] and the area under it ([fill]) for [time] and returns the end point, or
     * null when nothing is visible yet. Reuses the paths, so nothing is allocated per frame.
     */
    fun trace(line: Path, fill: Path, time: ChartTime, frame: ChartFrame, scale: Float): Offset? {
        line.reset()
        fill.reset()
        if (time.end <= startMillis) return null
        val pxPerMilli = (frame.right - frame.left) / time.span
        val dx = stepMillis * pxPerMilli
        fun xOf(t: Long) = frame.right - (time.end - t) * pxPerMilli
        val lastIndex = values.size - 1
        val from = Math.floorDiv(time.start - startMillis, stepMillis).coerceIn(0L, lastIndex.toLong()).toInt()
        val endPosition = (time.end - startMillis).toFloat() / stepMillis
        val tipIndex = floor(endPosition).toInt().coerceAtMost(lastIndex)
        var x = xOf(startMillis + from * stepMillis)
        var y = frame.yOf(values[from], scale)
        line.moveTo(x, y)
        fill.moveTo(x, frame.bottom)
        fill.lineTo(x, y)
        for (k in from until tipIndex) {
            segment(line, fill, k, x, dx, frame, scale, 1f)
            x += dx
            y = frame.yOf(values[k + 1], scale)
        }
        if (tipIndex < lastIndex) {
            val u = endPosition - tipIndex
            if (u > 0) {
                val end = segment(line, fill, tipIndex, x, dx, frame, scale, u)
                x = end.x
                y = end.y
            }
        } else {
            x = frame.right
            line.lineTo(x, y)
            fill.lineTo(x, y)
        }
        fill.lineTo(x, frame.bottom)
        fill.close()
        return Offset(x, y)
    }

    /** Emits the first [u] of segment [k] (a cubic split by de Casteljau) and returns its end. */
    private fun segment(
        line: Path,
        fill: Path,
        k: Int,
        x0: Float,
        dx: Float,
        frame: ChartFrame,
        scale: Float,
        u: Float
    ): Offset {
        val y0 = frame.yOf(values[k], scale)
        val y1 = frame.yOf(values[k] + slopes[k] / 3, scale)
        val y2 = frame.yOf(values[k + 1] - slopes[k + 1] / 3, scale)
        val y3 = frame.yOf(values[k + 1], scale)
        val x1 = x0 + dx / 3
        val x2 = x0 + dx * 2 / 3
        val x3 = x0 + dx
        val ax = lerp(x0, x1, u); val ay = lerp(y0, y1, u)
        val bx = lerp(x1, x2, u); val by = lerp(y1, y2, u)
        val cx = lerp(x2, x3, u); val cy = lerp(y2, y3, u)
        val abx = lerp(ax, bx, u); val aby = lerp(ay, by, u)
        val bcx = lerp(bx, cx, u); val bcy = lerp(by, cy, u)
        val ex = lerp(abx, bcx, u); val ey = lerp(aby, bcy, u)
        line.cubicTo(ax, ay, abx, aby, ex, ey)
        fill.cubicTo(ax, ay, abx, aby, ex, ey)
        return Offset(ex, ey)
    }

    companion object {
        fun of(
            samples: List<FileJobSpeedSample>,
            windowMillis: Long = FileJobSpeedTracker.DEFAULT_WINDOW_MILLIS,
            stepMillis: Long = StepMillis,
            graceMillis: Long = FileJobRateSmoother.STALL_GRACE_MILLIS,
            timeConstantMillis: Long = FileJobRateSmoother.DEFAULT_TIME_CONSTANT_MILLIS
        ): SpeedSeries? {
            if (samples.isEmpty()) return null
            val first = samples.first()
            val last = samples.last()
            // A history shorter than the window started with this job, which ramped up from rest;
            // a trimmed one just begins wherever the tracker cut it.
            val fromRest = last.timeMillis - first.timeMillis < windowMillis + RestCutoffMillis
            val origin = first.timeMillis - RampMillis
            val earliest = last.timeMillis - windowMillis - MaxHistorySlackMillis
            val start = Math.floorDiv(
                maxOf(if (fromRest) origin else first.timeMillis, earliest), stepMillis
            ) * stepMillis
            val decayMillis = graceMillis + (timeConstantMillis * ln(1 / DecayFloor)).toLong()
            val end = ceilDiv(last.timeMillis + decayMillis - start, stepMillis) * stepMillis + start
            val count = ((end - start) / stepMillis).toInt() + 2
            val values = FloatArray(count)
            var j = 0
            for (i in 0 until count - 1) {
                val t = start + i * stepMillis
                values[i] = when {
                    t < first.timeMillis -> if (fromRest) {
                        first.rate * ((t - origin).toFloat() / RampMillis).coerceIn(0f, 1f)
                    } else first.rate
                    else -> {
                        while (j + 1 < samples.size && samples[j + 1].timeMillis <= t) j++
                        val a = samples[j]
                        val decayed = decay(a, t, graceMillis, timeConstantMillis)
                        if (j + 1 < samples.size) {
                            // Blends the stall decay into the next sample, so a resumed transfer
                            // does not rewrite the stall it already showed.
                            val b = samples[j + 1]
                            val fraction = (t - a.timeMillis).toFloat() / (b.timeMillis - a.timeMillis)
                            val gap = b.rate - decay(a, b.timeMillis, graceMillis, timeConstantMillis)
                            decayed + gap * fraction
                        } else decayed
                    }
                }.coerceAtLeast(0f)
            }
            values[count - 1] = 0f
            smooth(values)
            return SpeedSeries(
                start, stepMillis, values, monotoneSlopes(values), origin, last.timeMillis
            )
        }

        /**
         * One [1, 2, 1] pass: calms the jitter of bursty small-file updates without moving peaks
         * in time or leaving the range of the data.
         */
        private fun smooth(values: FloatArray) {
            var previous = values[0]
            for (i in 1 until values.size - 1) {
                val current = values[i]
                values[i] = (previous + 2 * current + values[i + 1]) / 4
                previous = current
            }
        }

        private val FileJobSpeedSample.rate: Float
            get() = if (bytesPerSecond.isNaN() || bytesPerSecond < 0) 0f else bytesPerSecond

        private fun decay(
            sample: FileJobSpeedSample,
            timeMillis: Long,
            graceMillis: Long,
            timeConstantMillis: Long
        ): Float {
            val silent = timeMillis - sample.timeMillis - graceMillis
            return if (silent <= 0) {
                sample.rate
            } else {
                sample.rate * exp(-silent.toFloat() / timeConstantMillis)
            }
        }
    }
}

/**
 * Fritsch–Carlson tangents for unit-spaced [values]: the Hermite spline through them is monotone
 * on every interval, so it cannot dip below zero or overshoot a peak.
 */
internal fun monotoneSlopes(values: FloatArray): FloatArray {
    val n = values.size
    val slopes = FloatArray(n)
    if (n < 2) return slopes
    val secants = FloatArray(n - 1) { values[it + 1] - values[it] }
    slopes[0] = secants[0]
    slopes[n - 1] = secants[n - 2]
    for (k in 1 until n - 1) {
        val before = secants[k - 1]
        val after = secants[k]
        slopes[k] = if (before * after <= 0) 0f else (before + after) / 2
    }
    for (k in 0 until n - 1) {
        val secant = secants[k]
        if (secant == 0f) {
            slopes[k] = 0f
            slopes[k + 1] = 0f
            continue
        }
        val a = slopes[k] / secant
        val b = slopes[k + 1] / secant
        val magnitude = a * a + b * b
        if (magnitude > 9) {
            val tau = 3 / sqrt(magnitude)
            slopes[k] = tau * a * secant
            slopes[k + 1] = tau * b * secant
        }
    }
    return slopes
}

internal fun hermite(y0: Float, y1: Float, m0: Float, m1: Float, u: Float): Float {
    val u2 = u * u
    val u3 = u2 * u
    return (2 * u3 - 3 * u2 + 1) * y0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * y1 +
        (u3 - u2) * m1
}

/** The smallest 1, 2, 2.5 or 5 × 10ⁿ that is at least [value]; zero for no value. */
internal fun niceCeil(value: Float): Float {
    if (!(value > 0) || value.isInfinite()) return 0f
    val magnitude = 10.0.pow(floor(log10(value.toDouble())))
    // Tolerates float noise so 2000 stays 2000 rather than becoming 2500.
    val fraction = value / magnitude * (1 - 1e-6)
    val nice = when {
        fraction <= 1 -> 1.0
        fraction <= 2 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5 -> 5.0
        else -> 10.0
    }
    return (nice * magnitude).toFloat()
}

/**
 * The top of the axis for [peak]: grows as soon as the curve needs more room, but only shrinks
 * once the curve fits in half of it, so the scale does not pump as peaks leave the window.
 */
internal fun nextScaleTarget(current: Float, peak: Float, minimum: Float = MinScale): Float {
    val wanted = maxOf(peak * Headroom, minimum)
    val needed = niceCeil(wanted)
    return when {
        current <= 0 || needed > current -> needed
        wanted <= current * ShrinkFraction -> needed
        else -> current
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)

internal const val StepMillis = 500L
internal const val LatencyMillis = 1_000L
internal const val MinSpanMillis = 10_000L
private const val RampMillis = 1_000L
private const val RestCutoffMillis = 3_000L
private const val MaxHistorySlackMillis = 10_000L
private const val DecayFloor = 1e-3
private const val RebaseGapMillis = 500L
private const val ScaleTimeConstantMillis = 180f
private const val MinScale = 1_000f
private const val Headroom = 1.1f
private const val ShrinkFraction = 0.5f
private const val SignalFloor = 1f
private const val FadeFraction = 0.16f
private val LineWidth = 2.5.dp
private val DotRadius = 6.dp
private val DotRing = 2.dp
private val LabelGap = 2.dp
