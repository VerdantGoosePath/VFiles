package me.zhanghai.android.files.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.getExternalStorageDirectory
import me.zhanghai.android.files.util.valueCompat

/** Standard-folder enablement uses the original parcelled per-folder override model. */
class StandardDirectoryListFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                StandardDirectorySettingsScreen(onNavigateUp = { requireActivity().finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StandardDirectorySettingsScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val directories by StandardDirectoriesLiveData.observeAsState(StandardDirectoriesLiveData.valueCompat)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_standard_directory_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.settings_expressive_back_24dp),
                            contentDescription = stringResource(R.string.settings_expressive_back))
                    }
                },
                scrollBehavior = scroll
            )
        },
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 24.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        context.getString(R.string.settings_standard_directories_title),
                        directories.map { directory ->
                            ExpressiveSettingRow(
                                key = directory.key,
                                title = directory.getTitle(context),
                                summary = getExternalStorageDirectory(directory.relativePath).toString(),
                                checked = directory.isEnabled,
                                iconRes = directory.iconRes,
                                onClick = { setDirectoryEnabled(directory, !directory.isEnabled) }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun setDirectoryEnabled(directory: StandardDirectory, enabled: Boolean) {
    val overrides = Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat.toMutableList()
    val index = overrides.indexOfFirst { it.id == directory.key }
    if (index >= 0) {
        overrides[index] = overrides[index].copy(isEnabled = enabled)
    } else {
        overrides += directory.toSettings().copy(isEnabled = enabled)
    }
    Settings.STANDARD_DIRECTORY_SETTINGS.putValue(overrides)
}

