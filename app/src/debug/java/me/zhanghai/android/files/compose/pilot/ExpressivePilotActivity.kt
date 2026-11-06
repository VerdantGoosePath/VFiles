package me.zhanghai.android.files.compose.pilot

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.compose.components.BreadcrumbUi
import me.zhanghai.android.files.compose.components.FileEntryUi
import me.zhanghai.android.files.compose.components.PreferenceRowUi
import me.zhanghai.android.files.compose.components.StorageDestinationUi
import me.zhanghai.android.files.compose.components.VFilesAdaptivePathNavigation
import me.zhanghai.android.files.compose.components.VFilesBreadcrumbs
import me.zhanghai.android.files.compose.components.VFilesFileProgress
import me.zhanghai.android.files.compose.components.VFilesFileRow
import me.zhanghai.android.files.compose.components.VFilesPreferenceGroup
import me.zhanghai.android.files.compose.theme.VFilesAppearance
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.theme.custom.ThemeColor

/**
 * DEBUG ONLY: launch with
 * adb shell am start -n fr.vroot.vfiles.fork/me.zhanghai.android.files.compose.pilot.ExpressivePilotActivity
 * No real filesystem, settings or credentials are edited. View label proves classic View hosting;
 * the ComposeView has a lifecycle-scoped composition, not Compose content in an unmanaged window.
 */
class ExpressivePilotActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hostLabel = TextView(this).apply {
            text = "View host · Compose Expressive isolation"
            contentDescription = text
            setPadding(16, 12, 16, 12)
        }
        root.addView(hostLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent { ExpressivePilot() }
        }
        root.addView(compose, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }
}

/** Only pilot-local interaction state. Production screens hoist state from ViewModels/SettingLiveData. */
@Composable
fun ExpressivePilot(forced: VFilesAppearance? = null) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var black by rememberSaveable { mutableStateOf(false) }
    var followPrefs by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var active by rememberSaveable { mutableStateOf("local") }
    val chosen = forced ?: if (followPrefs) null else VFilesAppearance(
        color = ThemeColor.MATERIAL_TEAL, dark = dark, black = black
    )
    VFilesTheme(appearance = chosen) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Material 3 Expressive", style = MaterialTheme.typography.headlineLargeEmphasized)
                Toggle("Follow saved theme", followPrefs) { followPrefs = it }
                if (!followPrefs && forced == null) {
                    Toggle("Dark", dark) { dark = it }
                    Toggle("Black surfaces", black) { black = it }
                }
                VFilesPreferenceGroup(
                    "Preview settings",
                    listOf(
                        PreferenceRowUi("active", "Primary storage",
                            "Current location and free space", "Internal"),
                        PreferenceRowUi("quiet", "Remote access",
                            "FTP, SFTP, SMB, WebDAV", enabled = enabled),
                        PreferenceRowUi("other", "Display density",
                            "Tap to toggle selection", "List")
                    ),
                    onSelect = { selected = !selected }
                )
                Toggle("File row enabled", enabled) { enabled = it }
                VFilesBreadcrumbs(
                    segments = listOf(BreadcrumbUi("root", "Storage"), BreadcrumbUi("docs", "Documents")),
                    onNavigate = { active = "local" }
                )
                VFilesFileRow(FileEntryUi("example", "Financial report.pdf",
                    "2.3 MB · PDF", selected = selected, enabled = enabled),
                    onOpen = { selected = !selected }, onLongPress = { selected = !selected })
                VFilesFileProgress(
                    title = "Copy (sample progress, no file operation)",
                    transferredBytes = 25L, totalBytes = 100L, onCancel = {}
                )
                VFilesAdaptivePathNavigation(
                    destinations = listOf(
                        StorageDestinationUi("local", "Local"),
                        StorageDestinationUi("remote", "Remote"),
                        StorageDestinationUi("bookmarks", "Bookmarks")
                    ),
                    activeId = active, onSelect = { active = it }
                ) {
                    Text("Selected: $active", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview(name = "393dp light", widthDp = 393, heightDp = 840, fontScale = 1f, showBackground = true)
@Preview(name = "600dp 130%", widthDp = 600, heightDp = 900, fontScale = 1.3f, showBackground = true)
@Preview(name = "840dp RTL 200%", widthDp = 840, heightDp = 780, fontScale = 2f, locale = "ar", showBackground = true)
@Composable
private fun ExpressivePilotLightPreview() {
    ExpressivePilot(forced = VFilesAppearance(ThemeColor.MATERIAL_TEAL, dark = false))
}

@Preview(name = "393dp dark AMOLED", widthDp = 393, heightDp = 840, showBackground = true)
@Composable
private fun ExpressivePilotBlackPreview() {
    ExpressivePilot(forced = VFilesAppearance(ThemeColor.MATERIAL_INDIGO, dark = true, black = true))
}
