package me.zhanghai.android.files.theme.custom

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.takisoft.preferencex.PreferenceFragmentCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.colorpicker.BaseColorPreference
import me.zhanghai.android.files.colorpicker.ColorPreferenceDialogFragment
import me.zhanghai.android.files.compat.getColorCompat

class AppIconColorPreference : BaseColorPreference {
    private lateinit var stringValue: String
    private lateinit var defaultStringValue: String

    override val entryValues: IntArray
        get() = AppIconColor.entries.map { it.displayColor() }.toIntArray()

    @get:ColorInt
    override val leadingDynamicColor: Int
        get() = AppIconColor.DEFAULT.displayColor()

    override val leadingColorLabelResource: Int = R.string.color_picker_default_color_label

    @get:ColorInt
    override val defaultValue: Int
        get() = defaultStringValue.toAppIconColor().displayColor()

    @get:ColorInt
    override var value: Int
        get() = stringValue.toAppIconColor().displayColor()
        set(value) {
            // RGB alone cannot distinguish DEFAULT from a matching blue preset.
            selectEntry(entryValues.indexOf(value).coerceAtLeast(0))
        }

    override val selectedEntryIndex: Int
        get() = stringValue.toAppIconColor().ordinal

    override val defaultEntryIndex: Int
        get() = defaultStringValue.toAppIconColor().ordinal

    override fun selectEntry(index: Int) {
        val color = AppIconColor.entries.getOrElse(index) { AppIconColor.DEFAULT }
        stringValue = color.ordinal.toString()
        persistString(stringValue)
        notifyChanged()
    }

    @ColorInt
    private fun AppIconColor.displayColor(): Int = context.getColorCompat(backgroundColorRes)

    private fun String.toAppIconColor(): AppIconColor =
        AppIconColor.entries.getOrElse(toIntOrNull() ?: 0) { AppIconColor.DEFAULT }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)
    constructor(
        context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? =
        a.getString(index).also { defaultStringValue = it!! }

    override fun onSetInitialValue(defaultValue: Any?) {
        stringValue = getPersistedString(defaultValue as String?)
    }

    companion object {
        init {
            PreferenceFragmentCompat.registerPreferenceFragment(
                AppIconColorPreference::class.java, ColorPreferenceDialogFragment::class.java
            )
        }
    }
}
