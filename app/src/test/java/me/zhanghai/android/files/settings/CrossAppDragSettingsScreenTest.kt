package me.zhanghai.android.files.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.util.valueCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders and behaviour of the drag to other apps settings page; see app/build/m3e-previews/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class CrossAppDragSettingsScreenTest {
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

    @After fun restoreDefaults() {
        Settings.FILE_LIST_CROSS_APP_DRAG.putValue(true)
        Settings.FILE_LIST_CROSS_APP_DRAG_HOLD_MILLIS.putValue(2000)
        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_DIRECTORIES.putValue(true)
        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_MULTIPLE.putValue(true)
    }

    private fun show(dark: Boolean) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                CrossAppDragSettingsScreen(onNavigateUp = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/m3e-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun defaultsRender() {
        // The exact entry dialog is not opened here: under Robolectric any text field inside a
        // dialog keeps Compose from ever going idle. Its parsing is covered by CrossAppDragTest.
        show(dark = false)
        composeRule.onNodeWithText("2.0 s").assertExists()
        composeRule.onNodeWithText("You can drag any selection of files and folders.").assertExists()
        capture("cross-app-drag-light")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun aStoredCustomValueShowsExactly() {
        Settings.FILE_LIST_CROSS_APP_DRAG_HOLD_MILLIS.putValue(1300)
        show(dark = false)
        composeRule.onNodeWithText("1.3 s").assertExists()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun turningTheMainSwitchOffDisablesEverythingBelow() {
        show(dark = true)
        composeRule.onNodeWithText("Use drag to other apps").performClick()
        composeRule.waitForIdle()
        assertFalse(Settings.FILE_LIST_CROSS_APP_DRAG.valueCompat)
        composeRule.onNodeWithText("Folders").assertIsNotEnabled()
        composeRule.onNodeWithText("Multiple items").assertIsNotEnabled()
        composeRule.onNodeWithText("Folders").performClick()
        assertEquals(true, Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_DIRECTORIES.valueCompat)
        capture("cross-app-drag-off-dark")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun theSummaryFollowsTheItemRules() {
        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_DIRECTORIES.putValue(false)
        Settings.FILE_LIST_CROSS_APP_DRAG_ALLOW_MULTIPLE.putValue(false)
        show(dark = false)
        composeRule.onNodeWithText("You can drag one file at a time.").assertExists()
        composeRule.onNodeWithText("Folders").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You can drag one file or one folder at a time.").assertExists()
    }
}
