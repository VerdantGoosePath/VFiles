package me.zhanghai.android.files.filelist

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.filejob.FileJobProgress
import me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.Collator
import java.time.Instant

/**
 * JVM renders of the real browser screen for design review (not golden tests). Images land in
 * app/build/m3e-previews/ so every theme, width and mode can be inspected without a device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class FileBrowserScreenPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun installProvider() {
        runCatching { FileSystemProvider.installDefaultProvider(LinuxFileSystemProvider) }
    }

    private val trail = listOf(
        BreadcrumbUi("0", "Internal storage"), BreadcrumbUi("1", "Download")
    )

    private fun files(): List<FileItem> = listOf(
        item("Camera", directory = true),
        item("Documents", directory = true),
        item("Receipts 2025", directory = true),
        item("Quarterly report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 482_113),
        item("backup-archive.zip", "application/zip", 52_428_800),
        item("notes.txt", "text/plain", 2_048),
        item("budget.csv", "text/csv", 18_311)
    )

    private fun item(
        name: String,
        mimeType: String = "inode/directory",
        size: Long = 0,
        directory: Boolean = false
    ): FileItem {
        val path: Path = Paths.get("/storage/emulated/0/Download/$name")
        val attributes = PreviewFileAttributes(directory, size)
        return FileItem(
            path, Collator.getInstance().getCollationKey(name), attributes, null, null, false,
            mimeType.asMimeType()
        )
    }

    private fun render(
        name: String,
        dark: Boolean = false,
        chrome: FileBrowserChromeState = FileBrowserChromeState(
            breadcrumbs = trail, subtitle = "3 folders, 4 files", currentReadOnly = false
        ),
        content: FileBrowserContentState = FileBrowserContentState(
            pathKey = "/storage/emulated/0/Download", files = files(), loading = false
        ),
        jobs: List<FileJobProgress> = emptyList(),
        fabExpanded: Boolean = false,
        navigation: FileBrowserNavigationState = FileBrowserNavigationState(
            items = previewNavigationItems,
            currentPath = Paths.get("/storage/emulated/0/Download")
        ),
        root: Int = 0
    ) {
        composeRule.setContent {
            Screen(dark) {
                FileBrowserScreen(
                    chrome = chrome,
                    content = content,
                    jobs = jobs,
                    navigation = navigation,
                    navigationListener = PreviewNavigation,
                    createMenuExpanded = fabExpanded,
                    actions = PreviewActions
                )
            }
        }
        capture(name, root)
    }

    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        VFilesTheme(appearance = VFilesAppearance(dark = dark), content = content)
    }

    private fun capture(name: String, root: Int = 0) {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val roots = composeRule.onAllNodes(isRoot())
        val node = if (root == -1) roots[roots.fetchSemanticsNodes().size - 1] else roots[root]
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = File("build/m3e-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneLight() = render("phone-light")

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun phoneDark() = render("phone-dark", dark = true)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneGrid() = render(
        "phone-grid",
        chrome = FileBrowserChromeState(
            breadcrumbs = trail, subtitle = "3 folders, 4 files", currentReadOnly = false,
            viewType = FileViewType.GRID
        ),
        content = FileBrowserContentState(
            pathKey = "/storage/emulated/0/Download", files = files(), loading = false,
            viewType = FileViewType.GRID
        )
    )

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneSelection() {
        val files = files()
        render(
            "phone-selection",
            chrome = FileBrowserChromeState(
                breadcrumbs = trail, subtitle = "3 folders, 4 files", currentReadOnly = false,
                selectedCount = 2
            ),
            content = FileBrowserContentState(
                pathKey = "/storage/emulated/0/Download", files = files, loading = false,
                selectedPaths = setOf(files[3].path, files[5].path)
            ),
            jobs = listOf(FileJobProgress(1, "Copying 3 files", "photo.jpg", 0.4f))
        )
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phonePaste() = render(
        "phone-paste",
        chrome = FileBrowserChromeState(
            breadcrumbs = trail, subtitle = "3 folders, 4 files", currentReadOnly = false,
            pasteCount = 3, pasteIsCopy = true
        )
    )

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneFabMenu() = render("phone-fab-menu", fabExpanded = true)

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneEmpty() = render(
        "phone-empty",
        chrome = FileBrowserChromeState(
            breadcrumbs = trail, subtitle = "Empty folder", currentReadOnly = false
        ),
        content = FileBrowserContentState(pathKey = "/empty", loading = false)
    )

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneError() = render(
        "phone-error",
        content = FileBrowserContentState(
            pathKey = "/error", loading = false, error = "Permission denied"
        )
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletRail() = render("tablet-rail")

    @Test @Config(qualifiers = "w1280dp-h800dp-night-xhdpi")
    fun tabletRailDark() = render("tablet-rail-dark", dark = true)

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletRailCollapsed() = render(
        "tablet-rail-collapsed",
        navigation = FileBrowserNavigationState(
            items = previewNavigationItems,
            currentPath = Paths.get("/storage/emulated/0/Download"),
            persistentExpanded = false
        )
    )

}

internal class PreviewFileAttributes(private val directory: Boolean, private val size: Long) :
    BasicFileAttributes {
    private val time = FileTime.from(Instant.parse("2025-11-03T10:15:30Z"))
    override fun lastModifiedTime(): FileTime = time
    override fun lastAccessTime(): FileTime = time
    override fun creationTime(): FileTime = time
    override fun isRegularFile(): Boolean = !directory
    override fun isDirectory(): Boolean = directory
    override fun isSymbolicLink(): Boolean = false
    override fun isOther(): Boolean = false
    override fun size(): Long = size
    override fun fileKey(): Any? = null
}

private class PreviewNavItem(
    override val id: Long,
    private val title: String,
    override val iconRes: Int?,
    private val subtitle: String? = null,
    private val checked: Boolean = false,
    override val isHeader: Boolean = false
) : me.zhanghai.android.files.navigation.NavigationItem() {
    override fun getTitle(context: android.content.Context): String = title
    override fun getSubtitle(context: android.content.Context): String? = subtitle
    override fun isChecked(listener: Listener): Boolean = checked
    override fun onClick(listener: Listener) {}
}

private val previewNavigationItems = listOf(
    PreviewNavItem(1, "Internal storage", me.zhanghai.android.files.R.drawable.sd_card_icon_white_24dp, "41.2 GB free of 128 GB"),
    PreviewNavItem(2, "USB drive", me.zhanghai.android.files.R.drawable.usb_icon_white_24dp, "12.8 GB free of 32 GB"),
    null,
    PreviewNavItem(3, "Folders", null, isHeader = true),
    PreviewNavItem(4, "Download", me.zhanghai.android.files.R.drawable.download_icon_white_24dp, checked = true),
    PreviewNavItem(5, "Pictures", me.zhanghai.android.files.R.drawable.image_icon_white_24dp),
    PreviewNavItem(6, "Documents", me.zhanghai.android.files.R.drawable.document_icon_white_24dp),
    null,
    PreviewNavItem(7, "FTP server", me.zhanghai.android.files.R.drawable.ftp_server_icon_white_24dp),
    PreviewNavItem(8, "Settings", me.zhanghai.android.files.R.drawable.settings_icon_white_24dp)
)

private object PreviewNavigation : me.zhanghai.android.files.navigation.NavigationItem.Listener {
    override val currentPath: Path get() = Paths.get("/storage/emulated/0/Download")
    override fun navigateTo(path: Path) {}
    override fun navigateToRoot(path: Path) {}
    override fun launchIntent(intent: android.content.Intent) {}
    override fun showToast(textRes: Int) {}
    override fun closeNavigationDrawer() {}
}

private object PreviewActions : FileBrowserScreenActions {
    override fun onBreadcrumb(id: String) {}
    override fun onViewType(viewType: FileViewType) {}
    override fun onSortBy(by: FileSortOptions.By) {}
    override fun onSortOrder(order: FileSortOptions.Order) {}
    override fun onDirectoriesFirst(directoriesFirst: Boolean) {}
    override fun onPathSpecificSort(pathSpecific: Boolean) {}
    override fun onShowHiddenFiles(show: Boolean) {}
    override fun onSearchActiveChange(active: Boolean) {}
    override fun onSearchQueryChange(query: String) {}
    override fun onSearchSubmit(query: String) {}
    override fun onRefresh() {}
    override fun onItemAction(file: FileItem, action: FileBrowserItemAction) {}
    override fun onRangeSelect(files: List<FileItem>) {}
    override fun onSetSelection(files: List<FileItem>, selected: Boolean) {}
    override fun onSelection(command: FileBrowserSelectionCommand) {}
    override fun onFolderAction(action: FileBrowserFolderAction) {}
    override fun onCreateMenuExpandedChange(expanded: Boolean) {}
    override fun onCreateFile() {}
    override fun onCreateDirectory() {}
    override fun onCancelJob(id: Int) {}
    override fun onNavigationOpenChange(open: Boolean) {}
    override fun onTogglePersistentNavigation() {}
}
