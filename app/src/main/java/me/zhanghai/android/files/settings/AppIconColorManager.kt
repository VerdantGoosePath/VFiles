package me.zhanghai.android.files.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ColorRes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.backgroundExecutor
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.util.valueCompat

/**
 * Switches the phone launcher entry between the `LauncherXxx` activity-aliases declared in the
 * manifest, one per [ThemeColor] except [ThemeColor.DYNAMIC].
 */
object AppIconColorManager {
    private const val ALIAS_PREFIX = "me.zhanghai.android.files.filelist.Launcher"

    /** Colours offered for the launcher icon, default first. */
    val colors: List<ThemeColor> = ThemeColor.entries.filter { it != ThemeColor.DYNAMIC }

    /** Adaptive icons, and therefore coloured backgrounds, need API 26. */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    internal fun aliasClassName(color: ThemeColor): String {
        val suffix = when (color) {
            ThemeColor.COLOR_PRIMARY, ThemeColor.DYNAMIC -> "Default"
            else -> color.name.removePrefix("MATERIAL_").split('_')
                .joinToString("") { it.lowercase().replaceFirstChar(Char::titlecase) }
        }
        return ALIAS_PREFIX + suffix
    }

    /** Matches the adaptive icon background in mipmap-anydpi-v26/launcher_icon_<name>.xml. */
    @ColorRes
    fun backgroundColorRes(color: ThemeColor): Int = when (color) {
        ThemeColor.COLOR_PRIMARY, ThemeColor.DYNAMIC -> R.color.launcher_icon_background_default
        ThemeColor.MATERIAL_RED -> R.color.launcher_icon_background_red
        ThemeColor.MATERIAL_PINK -> R.color.launcher_icon_background_pink
        ThemeColor.MATERIAL_PURPLE -> R.color.launcher_icon_background_purple
        ThemeColor.MATERIAL_DEEP_PURPLE -> R.color.launcher_icon_background_deep_purple
        ThemeColor.MATERIAL_INDIGO -> R.color.launcher_icon_background_indigo
        ThemeColor.MATERIAL_BLUE -> R.color.launcher_icon_background_blue
        ThemeColor.MATERIAL_LIGHT_BLUE -> R.color.launcher_icon_background_light_blue
        ThemeColor.MATERIAL_CYAN -> R.color.launcher_icon_background_cyan
        ThemeColor.MATERIAL_TEAL -> R.color.launcher_icon_background_teal
        ThemeColor.MATERIAL_GREEN -> R.color.launcher_icon_background_green
        ThemeColor.MATERIAL_LIGHT_GREEN -> R.color.launcher_icon_background_light_green
        ThemeColor.MATERIAL_LIME -> R.color.launcher_icon_background_lime
        ThemeColor.MATERIAL_YELLOW -> R.color.launcher_icon_background_yellow
        ThemeColor.MATERIAL_AMBER -> R.color.launcher_icon_background_amber
        ThemeColor.MATERIAL_ORANGE -> R.color.launcher_icon_background_orange
        ThemeColor.MATERIAL_DEEP_ORANGE -> R.color.launcher_icon_background_deep_orange
        ThemeColor.MATERIAL_BROWN -> R.color.launcher_icon_background_brown
        ThemeColor.MATERIAL_GREY -> R.color.launcher_icon_background_grey
        ThemeColor.MATERIAL_BLUE_GREY -> R.color.launcher_icon_background_blue_grey
    }

    fun applyAsync(context: Context, color: ThemeColor = Settings.APP_ICON_COLOR.valueCompat) {
        val applicationContext = context.applicationContext
        backgroundExecutor.execute { apply(applicationContext, color) }
    }

    /** Idempotent: only components whose effective state differs are touched. */
    @Synchronized
    fun apply(context: Context, color: ThemeColor = Settings.APP_ICON_COLOR.valueCompat) {
        val selected = if (isSupported) color else ThemeColor.COLOR_PRIMARY
        val packageManager = context.packageManager
        val selectedName = aliasClassName(selected)
        val defaultName = aliasClassName(ThemeColor.COLOR_PRIMARY)
        val names = colors.map { aliasClassName(it) }
        // Enable the new entry before disabling the old one so the app never has no launcher
        // entry, which some launchers treat as an uninstall and drop the home screen icon.
        val ordered = listOf(selectedName) + names.filter { it != selectedName }
        for (name in ordered) {
            val component = ComponentName(context.packageName, name)
            val enabled = name == selectedName
            val state = try {
                packageManager.getComponentEnabledSetting(component)
            } catch (e: IllegalArgumentException) {
                continue
            }
            val currentlyEnabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> name == defaultName
                else -> false
            }
            if (currentlyEnabled == enabled) {
                continue
            }
            val newState = when {
                // Return the default alias to its manifest state rather than pinning it.
                name == defaultName && enabled -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                component, newState, PackageManager.DONT_KILL_APP
            )
        }
    }
}
