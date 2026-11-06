package me.zhanghai.android.files.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.about.AboutActivity
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.storage.StorageListActivity
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

/**
 * Replaces the XML PreferenceFragment host. ActivityResult is registered with this Fragment,
 * not a transient Composable, so folder picking survives activity/process restoration.
 */
class SettingsFragment : Fragment() {
    private val directoryPicker = registerForActivityResult(
        FileListActivity.OpenDirectoryContract()
    ) { path ->
        if (path != null) Settings.FILE_LIST_DEFAULT_DIRECTORY.putValue(path)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                ExpressiveSettingsScreen(
                    onNavigateUp = { requireActivity().finish() },
                    onOpenDefaultDirectory = {
                        directoryPicker.launch(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
                    },
                    onOpenStorages = {
                        requireContext().startActivitySafe(StorageListActivity::class.createIntent())
                    },
                    onOpenStandardDirectories = {
                        requireContext().startActivitySafe(
                            StandardDirectoryListActivity::class.createIntent()
                        )
                    },
                    onOpenBookmarks = {
                        requireContext().startActivitySafe(
                            BookmarkDirectoryListActivity::class.createIntent()
                        )
                    },
                    onOpenDefaultApps = {
                        requireContext().startActivitySafe(DefaultAppsActivity::class.createIntent())
                    },
                    onOpenAbout = {
                        requireContext().startActivitySafe(AboutActivity::class.createIntent())
                    },
                    onOpenSystemLocales = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requireContext().startActivitySafe(
                                Intent(
                                    SystemSettings.ACTION_APP_LOCALE_SETTINGS,
                                    Uri.fromParts("package", requireContext().packageName, null)
                                )
                            )
                        }
                    },
                    onSetAppLocalesPre33 = { locales ->
                        (requireActivity() as SettingsActivity).setApplicationLocalesPre33(locales)
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Exactly one owner for theme synchronization. Never sync from Composable recomposition;
        // callbacks compare the currently applied theme and do nothing for identical values.
        Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.EDGE_TO_EDGE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        // MATERIAL_DESIGN_3 is intentionally not observed: its persisted value is inert.
    }
}
