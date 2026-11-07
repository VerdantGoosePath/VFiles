package me.zhanghai.android.files.ftpserver

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class FtpScreenshotSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<FtpServerActivity>()

    @Test fun captureRenderedSurface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.ftp_server_title)).assertExists()
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val destination = File(context.filesDir, "m3e-screenshots").apply { mkdirs() }
        File(destination, "ftp.png").outputStream().use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
