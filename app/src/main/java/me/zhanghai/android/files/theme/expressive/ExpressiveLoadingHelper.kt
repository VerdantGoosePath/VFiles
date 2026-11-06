package me.zhanghai.android.files.theme.expressive

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import com.google.android.material.loadingindicator.LoadingIndicator
import me.zhanghai.android.files.settings.MaterialDesign
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.util.WeakHashMap

/**
 * Replace ordinary indeterminate ProgressBars across all activities and dialog hierarchies with
 * the animated M3 Expressive morphing shape. Preserve determinate progress widgets and their data.
 * View tree scanning also covers views inflated after the Activity's onCreate callback.
 */
object ExpressiveLoadingHelper {
    fun install(activity: Activity) {
        if (Settings.MATERIAL_DESIGN.valueCompat != MaterialDesign.MATERIAL3_EXPRESSIVE) return
        val upgraded = WeakHashMap<ProgressBar, Boolean>()
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            fun visit(view: View) {
                if (view.javaClass == ProgressBar::class.java) {
                    val progress = view as ProgressBar
                    if (progress.isIndeterminate && upgraded.put(progress, true) == null) {
                        progress.indeterminateDrawable = LoadingIndicator(progress.context).drawable
                    }
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
            }
            visit(decor)
        }
    }
}
