package me.zhanghai.android.files.compose.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalMotionDurationScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.custom.wallpaperAccentColor

/**
 * Theme values are immutable and contain no Activity references. A null [appearance] binds to the
 * existing SettingLiveData keys (through lifecycle-aware Compose observation); non-null is ideal
 * for previews/tests and isolated dialogs. The legacy MATERIAL_DESIGN_3 preference is deliberately
 * ignored here: all Compose islands are expressive, while Views retain their own existing theme.
 */
data class VFilesAppearance(
    val color: ThemeColor = ThemeColor.COLOR_PRIMARY,
    val dark: Boolean = false,
    val black: Boolean = false,
    val reducedMotion: Boolean = false
)

data class VFilesSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color
)

private val lightSemantics = VFilesSemanticColors(
    success = Color(0xFF176B3B), onSuccess = Color.White,
    successContainer = Color(0xFFD5F5DE), onSuccessContainer = Color(0xFF062312),
    warning = Color(0xFF765500), onWarning = Color.White,
    warningContainer = Color(0xFFFFE3A8), onWarningContainer = Color(0xFF251900),
    info = Color(0xFF205EA6), onInfo = Color.White
)
private val darkSemantics = VFilesSemanticColors(
    success = Color(0xFF9BE0AC), onSuccess = Color(0xFF00391B),
    successContainer = Color(0xFF19512C), onSuccessContainer = Color(0xFFD5F5DE),
    warning = Color(0xFFFFD273), onWarning = Color(0xFF3D2900),
    warningContainer = Color(0xFF594200), onWarningContainer = Color(0xFFFFE3A8),
    info = Color(0xFFADCBFF), onInfo = Color(0xFF003262)
)

val LocalVFilesSemanticColors = staticCompositionLocalOf { lightSemantics }

/** Expressive corner roles; never use device pixel sizes for UI geometry. */
val VFilesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)

/** Keeps all 15 base and 15 emphasized Material 3 roles. */
val VFilesTypography = Typography()

/**
 * One expressive composition root for every new UI surface (C/D/E).
 * Child composables must consume MaterialTheme and LocalVFilesSemanticColors; do not call
 * Context.setTheme or recreate an Activity from a composition. Legacy view theme changes are
 * coordinated by CustomThemeHelper and NightModeHelper until the host screen is migrated.
 */
@Composable
fun VFilesTheme(
    appearance: VFilesAppearance? = null,
    content: @Composable () -> Unit
) {
    val persisted = rememberVFilesAppearance()
    val selected = appearance ?: persisted
    val context = LocalContext.current
    // A zero animator-duration scale is honored by Compose animations automatically; additionally
    // use standard spatial motion when the platform has animations disabled.
    val animationScale = LocalMotionDurationScale.current.scaleFactor
    val scheme = resolveVFilesColorScheme(context, selected)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalVFilesSemanticColors provides if (selected.dark) darkSemantics else lightSemantics
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = VFilesTypography,
            shapes = VFilesShapes,
            motionScheme = if (selected.reducedMotion || animationScale == 0f) {
                MotionScheme.standard()
            } else {
                MotionScheme.expressive()
            },
            content = content
        )
    }
}

/**
 * Pure-for-a-given-context palette resolver for previews and contrast checks.
 * DYNAMIC obtains the actual wallpaper extracted seed on Android 12+ if available (OEM-safe),
 * otherwise uses the OS Material You scheme; old devices fall back to the original primary.
 * Core tonal palettes guarantee paired on-colors rather than assigning the seed to every role.
 */
fun resolveVFilesColorScheme(context: Context, appearance: VFilesAppearance): ColorScheme {
    val dynamic = appearance.color == ThemeColor.DYNAMIC && Build.VERSION.SDK_INT >= 31
    val wallpaperSeed = if (dynamic) context.wallpaperAccentColor() else null
    val scheme = if (dynamic && wallpaperSeed == null) {
        if (appearance.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val selected = if (appearance.color == ThemeColor.DYNAMIC) ThemeColor.COLOR_PRIMARY
            else appearance.color
        val seed = wallpaperSeed ?: runCatching {
            ContextCompat.getColor(context, selected.resourceId)
        }.getOrElse { ContextCompat.getColor(context, ThemeColor.COLOR_PRIMARY.resourceId) }
        val tones = SchemeTonalSpot(Hct.fromInt(seed), appearance.dark, 0.0)
        (if (appearance.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = Color(tones.primary),
            onPrimary = Color(tones.onPrimary),
            primaryContainer = Color(tones.primaryContainer),
            onPrimaryContainer = Color(tones.onPrimaryContainer),
            inversePrimary = Color(tones.inversePrimary),
            secondary = Color(tones.secondary),
            onSecondary = Color(tones.onSecondary),
            secondaryContainer = Color(tones.secondaryContainer),
            onSecondaryContainer = Color(tones.onSecondaryContainer),
            tertiary = Color(tones.tertiary),
            onTertiary = Color(tones.onTertiary),
            tertiaryContainer = Color(tones.tertiaryContainer),
            onTertiaryContainer = Color(tones.onTertiaryContainer),
            error = Color(tones.error),
            onError = Color(tones.onError),
            errorContainer = Color(tones.errorContainer),
            onErrorContainer = Color(tones.onErrorContainer),
            background = Color(tones.background),
            onBackground = Color(tones.onBackground),
            surface = Color(tones.surface),
            onSurface = Color(tones.onSurface),
            surfaceVariant = Color(tones.surfaceVariant),
            onSurfaceVariant = Color(tones.onSurfaceVariant),
            surfaceTint = Color(tones.surfaceTint),
            inverseSurface = Color(tones.inverseSurface),
            inverseOnSurface = Color(tones.inverseOnSurface),
            outline = Color(tones.outline),
            outlineVariant = Color(tones.outlineVariant),
            scrim = Color(tones.scrim),
            surfaceDim = Color(tones.surfaceDim),
            surfaceBright = Color(tones.surfaceBright),
            surfaceContainerLowest = Color(tones.surfaceContainerLowest),
            surfaceContainerLow = Color(tones.surfaceContainerLow),
            surfaceContainer = Color(tones.surfaceContainer),
            surfaceContainerHigh = Color(tones.surfaceContainerHigh),
            surfaceContainerHighest = Color(tones.surfaceContainerHighest)
        )
    }
    return if (appearance.dark && appearance.black) scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF151515),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1B1B1B),
        surfaceContainerHighest = Color(0xFF262626)
    ) else scheme
}
