package me.zhanghai.android.files.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalMotionDurationScale
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.util.valueCompat

/**
 * Lifecycle-aware SettingLiveData -> Compose bridge. Observe only in the top-level composition:
 * observeAsState attaches at most one observer per call, removes it on disposal, and reflects
 * existing SharedPreferences writes without introducing a second persistence system.
 */
@Composable
fun <T> SettingLiveData<T>.observeCompose(): State<T> = observeAsState(valueCompat)

/**
 * Host configuration is authoritative for FOLLOW_SYSTEM/AUTO modes: AppCompat's
 * NightModeHelper already maps those modes and updates the Activity configuration.
 * Never call CustomThemeHelper.sync() or Activity.recreate() from recomposition.
 */
@Composable
fun rememberVFilesAppearance(): VFilesAppearance {
    val selectedColor by Settings.THEME_COLOR.observeCompose()
    val nightMode by Settings.NIGHT_MODE.observeCompose()
    val black by Settings.BLACK_NIGHT_MODE.observeCompose()
    val systemDark = isSystemInDarkTheme()
    val dark = when (nightMode) {
        NightMode.ON -> true
        NightMode.OFF -> false
        else -> systemDark
    }
    return VFilesAppearance(
        color = selectedColor,
        dark = dark,
        black = black,
        reducedMotion = LocalMotionDurationScale.current.scaleFactor == 0f
    )
}
