package me.zhanghai.android.files.ftpserver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

class FtpServerFragment : Fragment() {
    private val homeDirectoryPicker =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { path ->
            // A path selection is intentionally ignored while FTP is running. Its server
            // configuration is snapshotted on start and changing it live would be misleading.
            if (path != null
                && FtpServerService.stateLiveData.value
                    == FtpServerService.State.STOPPED) {
                Settings.FTP_SERVER_HOME_DIRECTORY.putValue(path)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                ExpressiveFtpServerScreen(
                    onNavigateUp = { requireActivity().finish() },
                    onPickHomeDirectory = {
                        homeDirectoryPicker.launch(Settings.FTP_SERVER_HOME_DIRECTORY.valueCompat)
                    }
                )
            }
        }
    }
}
