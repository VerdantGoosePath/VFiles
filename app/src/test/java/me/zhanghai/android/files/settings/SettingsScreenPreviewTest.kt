package me.zhanghai.android.files.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Design-review renders of the real settings screen; see app/build/m3e-previews/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class SettingsScreenPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun installApplication() {
        runCatching {
            java8.nio.file.spi.FileSystemProvider.installDefaultProvider(
                me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
            )
        }
        Class.forName("me.zhanghai.android.files.app.AppProviderKt")
            .getDeclaredField("application")
            .apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    private fun render(name: String, dark: Boolean, scrollTo: List<Int>) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                ExpressiveSettingsScreen(
                    onNavigateUp = {}, onOpenDefaultDirectory = {}, onOpenStorages = {},
                    onOpenStandardDirectories = {}, onOpenBookmarks = {}, onOpenDefaultApps = {},
                    onOpenFtpServer = {}, onOpenAbout = {}, onOpenSystemLocales = {},
                    onSetAppLocalesPre33 = {}
                )
            }
        }
        scrollTo.forEachIndexed { page, index ->
            if (index > 0) {
                composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val dir = File("build/m3e-previews").apply { mkdirs() }
            File(dir, "$name-$page.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneLight() = render("settings-light", dark = false, scrollTo = listOf(0, 2, 4))

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun phoneDark() = render("settings-dark", dark = true, scrollTo = listOf(0))
}
