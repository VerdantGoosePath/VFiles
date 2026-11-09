package me.zhanghai.android.files.filelist

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java8.nio.file.Paths
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.Collator

/**
 * Compose UI 1.12 throws "LayoutNode not found in RectList" when a reused lazy row's baseline is
 * read during measure from below its direct child (b/549552303). Scrolling a long folder reuses
 * every row slot, so this guards the file rows against that trigger.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h500dp-xxhdpi")
class FileBrowserScrollTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun installProvider() {
        runCatching { FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider) }
    }

    private fun files(): List<FileItem> = (0 until 120).map { index ->
        val directory = index % 5 == 0
        val name = if (directory) "Folder $index" else "File ${"x".repeat(index % 7)} $index.txt"
        FileItem(
            Paths.get("/storage/emulated/0/Download/$name"),
            Collator.getInstance().getCollationKey(name),
            PreviewFileAttributes(directory, index * 1_000L), null, null, false,
            (if (directory) "inode/directory" else "text/plain").asMimeType()
        )
    }

    @Test
    fun scrollingReusesRowsWithoutCrashing() {
        val state = FileBrowserContentState(pathKey = "/scroll", files = files(), loading = false)
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance()) {
                VFilesBrowserContent(
                    state = state,
                    onRefresh = {},
                    onItemAction = { _, _ -> },
                    onRangeSelect = {},
                    contentPadding = PaddingValues()
                )
            }
        }
        composeRule.waitForIdle()
        val list = composeRule.onNode(hasScrollAction())
        repeat(12) {
            list.performTouchInput { swipeUp(durationMillis = 120) }
            composeRule.waitForIdle()
        }
        repeat(12) {
            list.performTouchInput { swipeDown(durationMillis = 120) }
            composeRule.waitForIdle()
        }
    }
}
