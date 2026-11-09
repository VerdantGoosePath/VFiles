package me.zhanghai.android.files.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.theme.custom.ThemeColor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class AppIconColorSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun enabledAliases(): List<String> {
        val packageManager = context.packageManager
        return AppIconColorManager.colors.map { AppIconColorManager.aliasClassName(it) }
            .filter { name ->
                val component = ComponentName(context.packageName, name)
                when (packageManager.getComponentEnabledSetting(component)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                        packageManager.getActivityInfo(
                            component, PackageManager.MATCH_DISABLED_COMPONENTS
                        ).enabled
                    else -> false
                }
            }
    }

    @Test
    fun everyColorHasAManifestAlias() {
        for (color in AppIconColorManager.colors) {
            val component =
                ComponentName(context.packageName, AppIconColorManager.aliasClassName(color))
            val info = context.packageManager.getActivityInfo(
                component, PackageManager.MATCH_DISABLED_COMPONENTS
            )
            assertEquals(
                "me.zhanghai.android.files.filelist.FileListActivity", info.targetActivity
            )
            assertEquals(color == ThemeColor.COLOR_PRIMARY, info.enabled)
        }
    }

    @Test
    fun applyLeavesExactlyOneLauncherEnabled() {
        assertEquals(listOf("me.zhanghai.android.files.filelist.LauncherDefault"), enabledAliases())
        AppIconColorManager.apply(context, ThemeColor.MATERIAL_DEEP_PURPLE)
        assertEquals(
            listOf("me.zhanghai.android.files.filelist.LauncherDeepPurple"), enabledAliases()
        )
        AppIconColorManager.apply(context, ThemeColor.MATERIAL_DEEP_PURPLE)
        AppIconColorManager.apply(context, ThemeColor.MATERIAL_TEAL)
        assertEquals(listOf("me.zhanghai.android.files.filelist.LauncherTeal"), enabledAliases())
        AppIconColorManager.apply(context, ThemeColor.COLOR_PRIMARY)
        assertEquals(listOf("me.zhanghai.android.files.filelist.LauncherDefault"), enabledAliases())
    }
}
