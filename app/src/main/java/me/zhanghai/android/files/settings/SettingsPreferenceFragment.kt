/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import me.zhanghai.android.files.font.FontHelper
import me.zhanghai.android.files.util.valueCompat
import android.view.View
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference
    private var pendingFontImport: FontChoice? = null
    private val fontPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val choice = pendingFontImport
        pendingFontImport = null
        if (uri != null && choice != null) {
            if (FontHelper.importFont(requireContext(), choice, uri)) {
                Settings.FONT_CHOICE.putValue(choice)
            } else {
                android.widget.Toast.makeText(
                    requireContext(), R.string.settings_font_import_failed, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        preferenceScreen.findPreference<Preference>(getString(R.string.pref_key_font_choice))
            ?.setOnPreferenceChangeListener { _, value ->
                val choice = FontChoice.entries.getOrNull(value.toString().toIntOrNull() ?: -1)
                    ?: return@setOnPreferenceChangeListener false
                if (choice != FontChoice.SYSTEM_DEFAULT &&
                    !FontHelper.hasFont(requireContext(), choice)) {
                    pendingFontImport = choice
                    fontPicker.launch("*/*")
                    false
                } else {
                    true
                }
            }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.FONT_CHOICE.observe(viewLifecycleOwner, this::onFontChoiceChanged)
        Settings.MATERIAL_DESIGN.observe(viewLifecycleOwner, this::onMaterialDesignChanged)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
        Settings.EDGE_TO_EDGE.observe(viewLifecycleOwner, this::onEdgeToEdgeChanged)
    }

    private fun onFontChoiceChanged(fontChoice: FontChoice) {
        CustomThemeHelper.sync()
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesignChanged(materialDesign: MaterialDesign) {
        CustomThemeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onEdgeToEdgeChanged(isEdgeToEdge: Boolean) {
        CustomThemeHelper.sync()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }
}
