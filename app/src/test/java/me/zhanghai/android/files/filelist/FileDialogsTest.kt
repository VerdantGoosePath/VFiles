package me.zhanghai.android.files.filelist

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import java8.nio.file.Paths
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.Collator

/**
 * Behaviour and design-review renders of the Compose-bodied dialog fragments, hosted the way the
 * app hosts them: a DialogFragment shown from a parent fragment that implements the listener.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class FileDialogsTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private lateinit var host: HostFragment
    private lateinit var controller: org.robolectric.android.controller.ActivityController<HostActivity>

    @Before fun setUp() {
        runCatching { FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider) }
        // Settings reads the app-wide Application that AppProvider normally installs.
        Class.forName("me.zhanghai.android.files.app.AppProviderKt")
            .getDeclaredField("application")
            .apply { isAccessible = true }
            .set(null, org.robolectric.RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        val activity = controller.get()
        host = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun currentDialog(): DialogFragment? =
        host.childFragmentManager.fragments.filterIsInstance<DialogFragment>().firstOrNull()

    private fun capture(name: String) {
        idle()
        val decor = currentDialog()!!.dialog!!.window!!.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Stand-in for the dimmed browser behind the window.
        canvas.drawColor(0xFF8A8790.toInt())
        decor.draw(canvas)
        val dir = File("build/m3e-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun createDirectoryValidatesInlineAndKeepsDialogOpen() {
        CreateDirectoryDialogFragment.show(host)
        idle()
        composeRule.onNodeWithText(host.getString(R.string.file_create_directory_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(host.getString(android.R.string.ok)).performClick()
        idle()
        composeRule.onNodeWithText(host.getString(R.string.file_name_error_empty)).assertIsDisplayed()
        assertTrue(currentDialog() != null)
        capture("dialog-create-directory-error")

        composeRule.onNodeWithText(host.getString(R.string.file_browser_dialog_name_label))
            .performTextReplacement("  Taxes  ")
        composeRule.onNodeWithText(host.getString(android.R.string.ok)).performClick()
        idle()
        assertEquals(listOf("Taxes"), host.createdDirectories)
        assertTrue(currentDialog() == null)
    }

    @Test fun typedNameSurvivesRecreation() {
        CreateDirectoryDialogFragment.show(host)
        idle()
        composeRule.onNodeWithText(host.getString(R.string.file_browser_dialog_name_label))
            .performTextReplacement("Half typed")
        idle()
        controller.recreate()
        host = controller.get().supportFragmentManager.findFragmentByTag("host") as HostFragment
        idle()
        assertTrue(currentDialog() is CreateDirectoryDialogFragment)
        composeRule.onNodeWithText("Half typed").assertIsDisplayed()
    }

    @Test fun createDirectoryRejectsExistingName() {
        CreateDirectoryDialogFragment.show(host)
        idle()
        composeRule.onNodeWithText(host.getString(R.string.file_browser_dialog_name_label))
            .performTextReplacement("Camera")
        composeRule.onNodeWithText(host.getString(android.R.string.ok)).performClick()
        idle()
        composeRule.onNodeWithText(host.getString(R.string.file_name_error_already_exists))
            .assertIsDisplayed()
        assertTrue(host.createdDirectories.isEmpty())
    }

    @Test fun renamePrefillsNameAndRenders() {
        RenameFileDialogFragment.show(file("Quarterly report.docx"), host)
        idle()
        composeRule.onNodeWithText("Quarterly report.docx").assertIsDisplayed()
        capture("dialog-rename")
        composeRule.onNodeWithText("Quarterly report.docx").performTextReplacement("Q3 report.docx")
        composeRule.onNodeWithText(host.getString(android.R.string.ok)).performClick()
        idle()
        assertEquals(listOf("Quarterly report.docx" to "Q3 report.docx"), host.renamed)
    }

    @Test fun createArchiveAppendsExtensionAndHidesPasswordForTarXz() {
        CreateArchiveDialogFragment.show(fileItemSetOf(file("Camera", directory = true)), host)
        idle()
        capture("dialog-create-archive")
        composeRule.onNodeWithText(host.getString(R.string.file_create_archive_type_tar_xz))
            .performClick()
        idle()
        assertEquals(
            0, composeRule.onAllNodesWithTextCount(host.getString(R.string.file_create_archive_password))
        )
        composeRule.onNodeWithText(host.getString(android.R.string.ok)).performClick()
        idle()
        assertEquals(listOf("Camera.tar.xz"), host.archives)
    }

    @Test fun confirmDeleteRendersAndDeletes() {
        ConfirmDeleteFilesDialogFragment.show(
            fileItemSetOf(file("notes.txt"), file("budget.csv")), host
        )
        idle()
        capture("dialog-delete")
        composeRule.onNode(
            androidx.compose.ui.test.hasText(host.getString(R.string.delete)) and
                androidx.compose.ui.test.hasClickAction()
        ).performClick()
        idle()
        assertEquals(1, host.deleted.size)
        assertEquals(2, host.deleted.single())
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(
        text: String
    ): Int = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    private fun file(name: String, directory: Boolean = false): FileItem {
        val path = Paths.get("/storage/emulated/0/Download/$name")
        return FileItem(
            path, Collator.getInstance().getCollationKey(name), PreviewFileAttributes(directory, 1024),
            null, null, false,
            (if (directory) "inode/directory" else "application/octet-stream").asMimeType()
        )
    }
}

class HostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MaterialFiles_Material3)
        super.onCreate(savedInstanceState)
    }
}

class HostFragment : Fragment(), CreateDirectoryDialogFragment.Listener,
    RenameFileDialogFragment.Listener, CreateArchiveDialogFragment.Listener,
    ConfirmDeleteFilesDialogFragment.Listener {
    val createdDirectories = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    val archives = mutableListOf<String>()
    val deleted = mutableListOf<Int>()

    override fun hasFileWithName(name: String): Boolean = name == "Camera"
    override fun createDirectory(name: String) { createdDirectories += name }
    override fun renameFile(file: FileItem, newName: String) { renamed += file.name to newName }
    override fun archive(
        files: FileItemSet, name: String, format: Int, filter: Int, password: String?
    ) { archives += name }
    override fun deleteFiles(files: FileItemSet) { deleted += files.size }
}
