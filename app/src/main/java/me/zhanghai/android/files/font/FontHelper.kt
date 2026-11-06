package me.zhanghai.android.files.font

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import me.zhanghai.android.files.settings.FontChoice
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.io.File
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Apply a variable font to Views, preserving the individual TextView's weight and italic styling.
 *
 * The fonts are user-imported, not shipped in the publicly accessible source tree. In particular,
 * Google Sans Flex carries a restricted license and should not be committed to this public fork.
 * On API 26+, Typeface.Builder selects actual variable wght and opsz axes for each text style.
 * Older devices use the best available regular/bold synthetic fallback.
 */
object FontHelper {
    fun hasFont(context: Context, choice: FontChoice): Boolean =
        choice != FontChoice.SYSTEM_DEFAULT && path(context, choice).isFile

    private fun path(context: Context, choice: FontChoice) =
        File(context.filesDir, when (choice) {
            FontChoice.SYSTEM_DEFAULT -> "font-default.ttf"
            FontChoice.ROBOTO_FLEX -> "font-roboto-flex.ttf"
            FontChoice.GOOGLE_SANS_FLEX -> "font-google-sans-flex.ttf"
        })

    fun importFont(context: Context, choice: FontChoice, uri: Uri): Boolean {
        if (choice == FontChoice.SYSTEM_DEFAULT) return false
        val file = path(context, choice)
        val temporary = File(context.cacheDir, "font-import.tmp")
        return try {
            val source = context.contentResolver.openInputStream(uri) ?: return false
            source.use { input ->
                temporary.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    require(bytes in 1000L..20_000_000L) { "Invalid font size" }
                }
            }
            Typeface.createFromFile(temporary) // Reject invalid font files before replacing.
            require(temporary.copyTo(file, overwrite = true).length() > 0L)
            true
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    fun install(activity: Activity) {
        val choice = Settings.FONT_CHOICE.valueCompat
        if (choice == FontChoice.SYSTEM_DEFAULT) return
        val file = path(activity, choice)
        if (!file.isFile) return
        val applied = WeakHashMap<TextView, Boolean>()
        val cache = mutableMapOf<Triple<Int, Boolean, Int>, Typeface>()
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            fun visit(view: View) {
                if (view is TextView && applied.put(view, true) == null) {
                    val original = view.typeface ?: Typeface.DEFAULT
                    val weight = if (Build.VERSION.SDK_INT >= 28) original.weight
                        else if (original.isBold) 700 else 400
                    val italic = original.isItalic
                    val opticalSize = (view.textSize / view.resources.displayMetrics.scaledDensity)
                        .roundToInt().coerceIn(8, 144)
                    val key = Triple(weight, italic, opticalSize)
                    val variable = cache.getOrPut(key) {
                        try {
                            if (Build.VERSION.SDK_INT >= 26) {
                                Typeface.Builder(file)
                                    .setWeight(weight)
                                    .setItalic(italic)
                                    .setFontVariationSettings("'wght' $weight, 'opsz' $opticalSize")
                                    .build()
                            } else {
                                Typeface.create(Typeface.createFromFile(file),
                                    (if (original.isBold) Typeface.BOLD else Typeface.NORMAL) or
                                        (if (italic) Typeface.ITALIC else Typeface.NORMAL))
                            }
                        } catch (_: Exception) {
                            original
                        }
                    }
                    view.typeface = variable
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
            }
            visit(decor)
        }
    }
}
