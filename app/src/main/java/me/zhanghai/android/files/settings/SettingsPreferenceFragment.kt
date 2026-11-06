/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import me.zhanghai.android.files.provider.archive.archiver.ArchiveSettings
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        val zipPreference = preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_zip_engine)
        )!!
        zipPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            updateArchivePreferences(value.toString())
            true
        }
        updateArchivePreferences(ArchiveSettings.zipEngine())

        val advancedToggle = preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_advanced_toggle)
        )!!
        val advancedCategory = preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_advanced_category)
        )!!
        advancedToggle.setOnPreferenceClickListener {
            advancedCategory.isVisible = !advancedCategory.isVisible
            advancedToggle.setSummary(
                if (advancedCategory.isVisible) R.string.archive_advanced_open
                else R.string.archive_advanced_closed
            )
            true
        }

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
    }


    private fun updateArchivePreferences(engine: String) {
        val rawzip = engine != "ripzip"
        preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_rawzip_level)
        )?.isVisible = rawzip
        preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_ripzip_level)
        )?.isVisible = !rawzip
        preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_rawzip_method)
        )?.isVisible = rawzip
        preferenceScreen.findPreference<Preference>(
            getString(R.string.pref_key_archive_ripzip_method)
        )?.isVisible = !rawzip
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
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
        Settings.EDGE_TO_EDGE.observe(viewLifecycleOwner, this::onEdgeToEdgeChanged)
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
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
