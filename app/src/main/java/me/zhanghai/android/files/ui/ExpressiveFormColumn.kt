package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Responsive width-bound credential form; validation and persistence stay in protocol fragments. */
class ExpressiveFormColumn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val maximum = (760f * resources.displayMetrics.density).toInt()
        val capped = if (width > maximum) {
            View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.EXACTLY)
        } else widthMeasureSpec
        super.onMeasure(capped, heightMeasureSpec)
    }
}
