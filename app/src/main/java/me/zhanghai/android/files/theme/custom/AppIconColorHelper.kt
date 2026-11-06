package me.zhanghai.android.files.theme.custom

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

object AppIconColorHelper {
    private const val TAG = "AppIconColorHelper"

    fun sync(context: Context) {
        val selected = Settings.APP_ICON_COLOR.valueCompat
        val packageManager = context.packageManager
        val changes = AppIconColor.entries.mapNotNull { color ->
            val component = ComponentName(context.packageName, color.aliasClassName)
            val state = packageManager.getComponentEnabledSetting(component)
            val enabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> color == AppIconColor.DEFAULT
                else -> false
            }
            if (enabled == (color == selected)) null else component to (color == selected)
        }
        if (changes.isEmpty()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Switch aliases atomically on Android 13 and later.
                packageManager.setComponentEnabledSettings(changes.map { (component, enabled) ->
                    PackageManager.ComponentEnabledSetting(
                        component,
                        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                })
            } else {
                // Enable the new entry before disabling the old one.
                changes.filter { it.second }.forEach { (component, _) ->
                    packageManager.setComponentEnabledSetting(
                        component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                changes.filterNot { it.second }.forEach { (component, _) ->
                    packageManager.setComponentEnabledSetting(
                        component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not apply app icon preference", exception)
        }
    }
}
