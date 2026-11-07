package me.zhanghai.android.files.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.zhanghai.android.files.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Captures the actual app Activity and Compose/View host, not a recreated mock screen.
 * CI uploads PNG as design evidence; independent 320/393/840dp and accessibility
 * evaluations are still required before release approval.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenshotSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<SettingsActivity>()

    @Test fun captureRenderedSurface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The Activity adds its Compose Fragment with an asynchronous FragmentTransaction.
        // Wait until its real view is composed; immediate assertions race that commit.
        val expectedTitle = context.getString(R.string.settings_expressive_preview)
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(expectedTitle).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(expectedTitle).assertExists()
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val destination = File(context.filesDir, "m3e-screenshots").apply { mkdirs() }
        File(destination, "settings.png").outputStream().use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
