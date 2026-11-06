/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.colorpicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.MaterialPreferenceDialogFragmentCompat
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.withTheme

class ColorPreferenceDialogFragment : MaterialPreferenceDialogFragmentCompat() {
    override val preference: BaseColorPreference
        get() = super.preference as BaseColorPreference

    private lateinit var colors: IntArray
    // The fixed colors shown in the grid; this excludes the dynamic color when present.
    private lateinit var gridColors: IntArray
    // Non-null when the first of [colors] is the wallpaper/dynamic color, shown apart from the grid.
    private var dynamicColor: Int? = null
    private var checkedEntryIndex = 0
    private var defaultEntryIndex = 0

    private lateinit var paletteGrid: GridView
    private lateinit var dynamicColorSwatch: ColorSwatchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val preference = preference
            colors = preference.entryValues
            dynamicColor = preference.leadingDynamicColor
            checkedEntryIndex = preference.selectedEntryIndex
            defaultEntryIndex = preference.defaultEntryIndex
        } else {
            val state = savedInstanceState.getState<State>()
            colors = state.colors
            dynamicColor = state.dynamicColor
            checkedEntryIndex = state.checkedEntryIndex
            defaultEntryIndex = state.defaultEntryIndex
        }
        gridColors = if (dynamicColor != null && colors.isNotEmpty()) {
            colors.copyOfRange(1, colors.size)
        } else {
            colors
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(
            State(colors, dynamicColor, currentCheckedEntryIndex() ?: checkedEntryIndex, defaultEntryIndex)
        )
    }

    override fun onCreateDialogView(context: Context): View? =
        super.onCreateDialogView(context.withTheme(theme))

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        paletteGrid = ViewCompat.requireViewById(view, R.id.palette)
        paletteGrid.adapter = ColorPaletteAdapter(gridColors)
        paletteGrid.setOnItemClickListener { _, _, _, _ ->
            // The single-choice grid checks the tapped swatch itself; we just clear the dynamic one.
            if (dynamicColor != null) {
                dynamicColorSwatch.isChecked = false
            }
        }

        val dynamicColor = dynamicColor
        if (dynamicColor != null) {
            ViewCompat.requireViewById<View>(view, R.id.dynamicColorSection).isVisible = true
            ViewCompat.requireViewById<View>(view, R.id.dynamicColorDivider).isVisible = true
            ViewCompat.requireViewById<View>(view, R.id.presetColorsLabel).isVisible = true
            ViewCompat.requireViewById<TextView>(view, R.id.dynamicColorLabel)
                .setText(preference.leadingColorLabelResource)
            dynamicColorSwatch = ViewCompat.requireViewById(view, R.id.dynamicColorSwatch)
            dynamicColorSwatch.setColor(dynamicColor)
            dynamicColorSwatch.setOnClickListener {
                clearGridChoice()
                dynamicColorSwatch.isChecked = true
            }
        }

        if (dynamicColor != null && checkedEntryIndex == 0) {
            dynamicColorSwatch.isChecked = true
        } else {
            val checkedPosition = checkedEntryIndex - if (dynamicColor != null) 1 else 0
            if (checkedPosition in gridColors.indices) {
                paletteGrid.setItemChecked(checkedPosition, true)
            }
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        if (defaultEntryIndex in colors.indices) {
            builder.setNeutralButton(R.string.default_, null)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as AlertDialog).apply {
            if (defaultEntryIndex in colors.indices) {
                // Override the listener here so that we won't close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        selectEntry(defaultEntryIndex)
                    }
                }
            }
        }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) {
            return
        }
        val checkedIndex = currentCheckedEntryIndex() ?: return
        preference.selectEntry(checkedIndex)
    }

    private fun selectEntry(index: Int) {
        if (dynamicColor != null && index == 0) {
            clearGridChoice()
            dynamicColorSwatch.isChecked = true
            return
        }
        if (dynamicColor != null) {
            dynamicColorSwatch.isChecked = false
        }
        val gridPosition = index - if (dynamicColor != null) 1 else 0
        if (gridPosition in gridColors.indices) {
            paletteGrid.setItemChecked(gridPosition, true)
        }
    }

    private fun clearGridChoice() {
        val checkedPosition = paletteGrid.checkedItemPosition
        if (checkedPosition != AdapterView.INVALID_POSITION) {
            paletteGrid.setItemChecked(checkedPosition, false)
        }
    }

    private fun currentCheckedEntryIndex(): Int? {
        if (dynamicColor != null && ::dynamicColorSwatch.isInitialized
            && dynamicColorSwatch.isChecked) {
            return 0
        }
        if (::paletteGrid.isInitialized) {
            val checkedPosition = paletteGrid.checkedItemPosition
            if (checkedPosition != AdapterView.INVALID_POSITION) {
                return checkedPosition + if (dynamicColor != null) 1 else 0
            }
        }
        return null
    }

    @Parcelize
    private class State(
        val colors: IntArray,
        val dynamicColor: Int?,
        val checkedEntryIndex: Int,
        val defaultEntryIndex: Int
    ) : ParcelableState
}
