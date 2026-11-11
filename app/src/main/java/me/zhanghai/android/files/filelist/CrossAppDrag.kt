package me.zhanghai.android.files.filelist

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.DocumentsContract
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.treeDocumentUri
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.create

/** Bounds and steps for how long a selected item is held before it lifts into a drag. */
object CrossAppDragHold {
    const val MIN_MILLIS = 500
    const val MAX_MILLIS = 5000
    const val SLIDER_STEP_MILLIS = 500
    const val ENTRY_STEP_MILLIS = 100

    /** Any stored value, rounded to the 0.1 s entry precision and clamped to the range. */
    fun coerce(millis: Int): Int =
        ((millis.toFloat() / ENTRY_STEP_MILLIS).roundToInt() * ENTRY_STEP_MILLIS)
            .coerceIn(MIN_MILLIS, MAX_MILLIS)

    fun snapToSlider(millis: Float): Int =
        ((millis / SLIDER_STEP_MILLIS).roundToInt() * SLIDER_STEP_MILLIS)
            .coerceIn(MIN_MILLIS, MAX_MILLIS)

    fun formatSeconds(millis: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(millis / 1000.0)

    /**
     * Whether [text] may stand in the entry field while typing: one whole-second digit, then
     * optionally a decimal separator ('.' or ',') and one tenth digit.
     */
    fun isAcceptableInput(text: String): Boolean =
        text.isEmpty() || ENTRY_PATTERN.matches(text)

    private val ENTRY_PATTERN = Regex("""\d([.,]\d?)?""")

    /** Milliseconds for typed seconds, or null when the text is not a value in range. */
    fun parseSeconds(text: String): Int? {
        val seconds = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (seconds.scale() > 1) return null
        val millis = (seconds.toDouble() * 1000).roundToInt()
        return millis.takeIf { it in MIN_MILLIS..MAX_MILLIS }
    }
}

/**
 * Which selections may be dragged. With neither folders nor multiple items allowed only a
 * single file goes; folders alone add a single folder; multiple alone add several files.
 */
data class CrossAppDragRules(val allowDirectories: Boolean, val allowMultiple: Boolean) {
    fun allows(count: Int, directoryCount: Int, containsArchiveEntry: Boolean): Boolean = when {
        count <= 0 || containsArchiveEntry -> false
        directoryCount > 0 && !allowDirectories -> false
        count > 1 && !allowMultiple -> false
        else -> true
    }

    fun allows(files: Collection<FileItem>): Boolean = allows(
        files.size,
        files.count { it.attributes.isDirectory },
        // Archive entries have no provider URI another app could open.
        files.any { it.path.isArchivePath }
    )
}

/** Colours of the floating drag label, taken from the Compose theme at gesture time. */
class CrossAppDragShadowStyle(val containerColor: Int, val contentColor: Int)

/**
 * Starts a platform drag that other apps can accept: files travel as FileProvider URIs, folders
 * as tree URIs of the documents provider so the receiver can walk their contents. Only read
 * access is granted.
 */
fun View.startCrossAppDrag(files: List<FileItem>, style: CrossAppDragShadowStyle): Boolean {
    if (files.isEmpty()) return false
    val label = files.singleOrNull()?.name ?: resources.getQuantityString(
        R.plurals.file_browser_paste_items, files.size, files.size
    )
    val hasDirectory = files.any { it.attributes.isDirectory }
    val clipData = ClipData::class.create(
        label,
        files.map {
            if (it.attributes.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
            else it.mimeType.value
        }.distinct(),
        files.map {
            ClipData.Item(
                if (it.attributes.isDirectory) it.path.treeDocumentUri else it.path.fileProviderUri
            )
        }
    )
    var flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
    // A tree grant reaches the folder's children only as a prefix grant, as with SAF pickers.
    if (hasDirectory) flags = flags or View.DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION
    return try {
        startDragAndDrop(clipData, CrossAppDragShadowBuilder(this, label, style), null, flags)
    } catch (e: IllegalStateException) {
        e.printStackTrace()
        false
    }
}

private class CrossAppDragShadowBuilder(
    view: View,
    label: String,
    style: CrossAppDragShadowStyle
) : View.DragShadowBuilder(view) {
    private val context: Context = view.context
    private val height = dp(56f)
    private val padding = dp(24f)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.containerColor }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.contentColor
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val text = TextUtils.ellipsize(
        label, textPaint, dp(280f) - 2 * padding, TextUtils.TruncateAt.MIDDLE
    ).toString()
    private val width = ceil(textPaint.measureText(text) + 2 * padding).toInt()
        .coerceAtLeast(height.toInt())

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    )

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height.toInt())
        // The label rides just above the finger instead of under it.
        outShadowTouchPoint.set(width / 2, height.toInt())
    }

    override fun onDrawShadow(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height)
        canvas.drawRoundRect(bounds, height / 2, height / 2, backgroundPaint)
        val baseline = height / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, padding, baseline, textPaint)
    }
}
