package me.zhanghai.android.files.filelist

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Emulator screenshot evidence for the full MaterialExpressiveTheme browser screen.
 * This is NOT a pixel-golden test; a human must inspect each theme, font scale and width.
 */
@RunWith(AndroidJUnit4::class)
class FileBrowserScreenshotSmokeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun capturesRealBrowserScreenInLightAndDark() {
        var dark by mutableStateOf(false)
        var selected by mutableStateOf(0)
        val trail = listOf(BreadcrumbUi("0", "Internal storage"), BreadcrumbUi("1", "Download"))
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                FileBrowserScreen(
                    chrome = FileBrowserChromeState(
                        breadcrumbs = trail, subtitle = "3 folders", selectedCount = selected,
                        currentReadOnly = false
                    ),
                    content = FileBrowserContentState(loading = false),
                    jobs = emptyList(),
                    navigation = FileBrowserNavigationState(),
                    navigationListener = NoNavigation,
                    createMenuExpanded = false,
                    actions = RecordingActions()
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshots = File(context.filesDir, "m3e-screenshots").apply { mkdirs() }
        fun capture(name: String) {
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(screenshots, "browser-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        capture("light")
        composeRule.runOnIdle { selected = 2 }
        capture("light-selection")
        composeRule.runOnIdle { dark = true; selected = 0 }
        capture("dark")
    }
}
