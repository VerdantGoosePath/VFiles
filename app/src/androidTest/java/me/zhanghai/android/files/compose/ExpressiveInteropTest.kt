package me.zhanghai.android.files.compose

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.compose.pilot.ExpressivePilotActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requires an emulator/device. Compile in CI without claiming a screenshot/behavior pass.
 * A debug-only Activity keeps the legacy production routes and picker intents unaffected.
 */
@RunWith(AndroidJUnit4::class)
class ExpressiveInteropTest {
    @get:Rule
    val rule = createAndroidComposeRule<ExpressivePilotActivity>()

    @Test
    fun hostShowsExpressiveContentAndSelectionIsInteractive() {
        rule.onNodeWithText("Material 3 Expressive").assertExists()
        rule.onNodeWithText("Financial report.pdf").assertExists()
        rule.onNodeWithText("Financial report.pdf").performClick()
        rule.onNodeWithText("Follow saved theme").assertExists()
    }

    @Test
    fun settingsAndRemotePlaceholdersRemainVisible() {
        rule.onNodeWithText("Remote access").assertExists()
        rule.onNodeWithText("FTP, SFTP, SMB, WebDAV").assertExists()
    }
}
