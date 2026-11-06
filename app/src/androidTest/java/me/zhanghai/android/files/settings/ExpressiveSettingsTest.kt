package me.zhanghai.android.files.settings

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.util.valueCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a running device. Compiled by assembleDebugAndroidTest in branch CI. */
@RunWith(AndroidJUnit4::class)
class ExpressiveSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<SettingsActivity>()

    @Test fun appearancePreviewShownAndLegacyM3ToggleAbsent() {
        rule.onNodeWithText("Appearance preview").assertExists()
        rule.onNodeWithText("Material Design 3").assertDoesNotExist()
    }

    @Test fun typedBooleanPersistsAcrossActivityRecreation() {
        val original = Settings.FILE_LIST_ANIMATION.valueCompat
        try {
            rule.runOnUiThread { Settings.FILE_LIST_ANIMATION.putValue(!original) }
            rule.waitForIdle()
            rule.activityRule.scenario.recreate()
            rule.runOnIdle {
                assertEquals(!original, Settings.FILE_LIST_ANIMATION.valueCompat)
            }
        } finally {
            rule.runOnUiThread { Settings.FILE_LIST_ANIMATION.putValue(original) }
        }
    }
}
