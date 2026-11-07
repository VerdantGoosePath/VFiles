package me.zhanghai.android.files.filelist

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * API35 emulator screenshot evidence for actual MaterialExpressiveTheme browser controls.
 * This is NOT a pixel-golden test; a human must inspect each theme, font scale and width.
 */
@RunWith(AndroidJUnit4::class)
class FileBrowserScreenshotSmokeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun capturesRealBrowserChromeInLightAndDark() {
        var dark by mutableStateOf(false)
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                Column(Modifier.fillMaxWidth()) {
                    VFilesBrowserChrome(
                        state = FileBrowserChromeState(),
                        onBreadcrumb = {},
                        onSearchExpanded = {},
                        onSearchQuery = {},
                        onSearchSubmit = {},
                        onViewType = {},
                        onSortBy = {},
                        onSortOrder = {},
                        onDirectoriesFirst = {},
                        onPathSpecificSort = {},
                        onShowHiddenFiles = {},
                        onSelection = {}
                    )
                    BrowserSelectionBar(
                        state = FileBrowserChromeState(selectedCount = 2),
                        onSelection = {}
                    )
                }
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
        composeRule.runOnIdle { dark = true }
        capture("dark")
    }
}
