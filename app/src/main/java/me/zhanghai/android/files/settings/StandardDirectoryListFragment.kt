/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesScreenScaffold
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.getExternalStorageDirectory

/** Native expressive standard-folder switches, preserving the original storage settings format. */
class StandardDirectoryListFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                val directories by StandardDirectoriesLiveData.observeAsState(emptyList())
                // Capture changes to the underlying list even if the provider list remains stable.
                val preferences by Settings.STANDARD_DIRECTORY_SETTINGS.observeCompose()
                VFilesScreenScaffold(
                    title = getString(R.string.settings_standard_directory_list_title),
                    onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            if (directories.isEmpty()) {
                                Text(
                                    getString(R.string.storage_list_empty),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            directories.forEachIndexed { index, directory ->
                                val selected = preferences.firstOrNull { it.id == directory.key }
                                    ?.isEnabled ?: directory.isEnabled
                                SegmentedListItem(
                                    onClick = { toggle(directory, !selected) },
                                    shapes = ListItemDefaults.segmentedShapes(index, directories.size),
                                    content = {
                                        Text(
                                            directory.getTitle(requireContext()),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            getExternalStorageDirectory(directory.relativePath).toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        Switch(checked = selected, onCheckedChange = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggle(directory: StandardDirectory, selected: Boolean) {
        val settings = Settings.STANDARD_DIRECTORY_SETTINGS.value.orEmpty().toMutableList()
        val index = settings.indexOfFirst { it.id == directory.key }
        if (index >= 0) {
            settings[index] = settings[index].copy(isEnabled = selected)
        } else {
            settings += directory.toSettings().copy(isEnabled = selected)
        }
        Settings.STANDARD_DIRECTORY_SETTINGS.putValue(settings)
    }
}
