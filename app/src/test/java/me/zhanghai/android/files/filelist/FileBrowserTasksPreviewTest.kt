package me.zhanghai.android.files.filelist

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.filejob.FileJobKind
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.filejob.FileJobSpeedSample
import me.zhanghai.android.files.filejob.FileJobSpeedSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/**
 * Design-review renders of the task pill, list and details (not golden tests). The sheet bodies
 * are rendered directly on the sheet tone, since the modal window itself is not capturable here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class FileBrowserTasksPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = 1_000_000L

    private val copy = FileJobProgress(
        1, "Copying 512 files to \u201cBackup\u201d", "129 / 512", 0.25f, FileJobKind.COPY,
        totalBytes = 4_800_000_000, processedBytes = 1_200_000_000, totalFiles = 512,
        processedFiles = 128, currentFileName = "IMG_20250314_181502.jpg",
        startedAtMillis = now - 134_000, updatedAtMillis = now
    )

    private val extract = FileJobProgress(
        2, "Extracting \u201cphotos.zip\u201d to \u201cDownload\u201d", "1.9 GB / 2.3 GB", 0.82f,
        FileJobKind.EXTRACT, totalBytes = 2_300_000_000, processedBytes = 1_886_000_000,
        totalFiles = 1, processedFiles = 0, currentFileName = "photos.zip",
        startedAtMillis = now - 47_000, updatedAtMillis = now
    )

    private val deleting = FileJobProgress(
        3, "Preparing to delete 1,204 files (3.1 GB)", null, null, FileJobKind.DELETE,
        startedAtMillis = now - 3_000, updatedAtMillis = now
    )

    private val deletingFiles = FileJobProgress(
        4, "Deleting 1,204 files", "482 / 1,204", 0.4f, FileJobKind.DELETE,
        totalFiles = 1_204, processedFiles = 482,
        currentFileName = "thumbnail_cache_entry_0482_with_a_rather_long_generated_name.tmp",
        startedAtMillis = now - 21_000, updatedAtMillis = now
    )

    private val stalled = FileJobProgress(
        5, "Copying 3 files to \u201cNAS\u201d", "1 / 3", 0.37f, FileJobKind.COPY,
        totalBytes = 9_100_000_000, processedBytes = 3_367_000_000, totalFiles = 3,
        processedFiles = 1, currentFileName = "Holiday 2024.mkv",
        startedAtMillis = now - 410_000, updatedAtMillis = now - 12_000
    )

    private val nearlyDone = FileJobProgress(
        6, "Moving \u201cScreenshot_20250314-181502_com.example.verylongpackagename.png\u201d",
        null, 0.997f, FileJobKind.MOVE, totalBytes = 812_000_000, processedBytes = 809_600_000,
        totalFiles = 1, processedFiles = 0,
        currentFileName = "Screenshot_20250314-181502_com.example.verylongpackagename.activity.png",
        startedAtMillis = now - 19_000, updatedAtMillis = now
    )

    private val stalledSpeed = FileJobSpeedSnapshot(
        bytesPerSecond = 64_000.0, averageBytesPerSecond = 8_200_000.0,
        samples = (0..96).map { i ->
            val t = now - 60_000 + i * 500L
            FileJobSpeedSample(t, if (i < 72) 9_000_000f + 600_000f * sin(i / 5f) else 0f)
        }
    )

    private val speed = FileJobSpeedSnapshot(
        bytesPerSecond = 12_400_000.0,
        averageBytesPerSecond = 9_000_000.0,
        samples = (0..120).map { i ->
            val t = now - 60_000 + i * 500L
            val ramp = (i / 20f).coerceAtMost(1f)
            val wave = 1f + 0.18f * sin(i / 7f) + 0.08f * sin(i / 2.3f)
            FileJobSpeedSample(t, 11_000_000f * ramp * wave + 400_000f)
        }
    )

    private val pills: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TaskPill(listOf(copy), onClick = {})
            TaskPill(listOf(extract), onClick = {})
            TaskPill(listOf(copy, extract), onClick = {})
            TaskPill(listOf(copy, extract, deleting), onClick = {})
        }
    }

    private val details: @Composable () -> Unit = {
        TaskDetailsContent(copy, speed, now, onBack = {}, onCancel = {})
    }

    private val singleFile: @Composable () -> Unit = {
        TaskDetailsContent(
            extract,
            speed.copy(bytesPerSecond = 52_300_000.0, averageBytesPerSecond = 40_100_000.0),
            now, onBack = null, onCancel = {}
        )
    }

    private val preparing: @Composable () -> Unit = {
        TaskDetailsContent(deleting, FileJobSpeedSnapshot(), now, onBack = null, onCancel = {})
    }

    private val list: @Composable () -> Unit = {
        TaskListContent(
            listOf(copy, extract, deletingFiles, stalled, deleting),
            speeds = {
                when (it) {
                    3 -> FileJobSpeedSnapshot()
                    4 -> FileJobSpeedSnapshot(filesPerSecond = 38.4)
                    5 -> stalledSpeed
                    else -> speed
                }
            },
            nowMillis = now,
            onOpen = {}, onCancel = {}
        )
    }

    private val filesOnly: @Composable () -> Unit = {
        TaskDetailsContent(
            deletingFiles, FileJobSpeedSnapshot(filesPerSecond = 38.4), now, onBack = {},
            onCancel = {}
        )
    }

    private val stalledDetails: @Composable () -> Unit = {
        TaskDetailsContent(stalled, stalledSpeed, now, onBack = null, onCancel = {})
    }

    private val nearlyDoneDetails: @Composable () -> Unit = {
        TaskDetailsContent(
            nearlyDone, speed.copy(bytesPerSecond = 61_000_000.0, averageBytesPerSecond = 42_700_000.0),
            now, onBack = null, onCancel = {}
        )
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun pillLight() = render("task-pill-light", false, pills)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun pillDark() = render("task-pill-dark", true, pills)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun detailsLight() = render("task-details-light", false, details)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun detailsDark() = render("task-details-dark", true, details)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun singleFileLight() = render("task-details-single-light", false, singleFile)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun singleFileDark() = render("task-details-single-dark", true, singleFile)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun preparingLight() = render("task-details-preparing-light", false, preparing)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun listLight() = render("task-list-light", false, list)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun listDark() = render("task-list-dark", true, list)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun filesOnlyLight() = render("task-details-files-light", false, filesOnly)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun stalledDark() = render("task-details-stalled-dark", true, stalledDetails)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun nearlyDoneLight() = render("task-details-nearly-done-light", false, nearlyDoneDetails)

    @Test @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = 1.5f)
    fun detailsFontScale15() = render("task-details-font-1.5", false, details)

    @Test @Config(qualifiers = "w411dp-h1800dp-xhdpi", fontScale = 2.0f)
    fun detailsFontScale2() = render("task-details-font-2.0", false, details)

    @Test @Config(qualifiers = "w411dp-h1400dp-xhdpi", fontScale = 2.0f)
    fun listFontScale2() = render("task-list-font-2.0", false, list)

    @Test @Config(qualifiers = "w411dp-h1400dp-xhdpi", fontScale = 2.0f)
    fun pillFontScale2() = render("task-pill-font-2.0", false, pills)

    @Test @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-xxhdpi")
    fun detailsRtl() = render("task-details-rtl", false, details)

    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun detailsTablet() = render("task-details-tablet", false, details)

    private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            VFilesTheme(appearance = VFilesAppearance(dark = dark)) {
                // Sheets sit on surfaceContainerLow; render on the same tone.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(top = 24.dp)) { content() }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val bitmap = composeRule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val dir = File("build/m3e-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
