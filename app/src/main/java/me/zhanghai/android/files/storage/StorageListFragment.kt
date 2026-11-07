/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.VFilesMessageState
import me.zhanghai.android.files.compose.components.VFilesScreenScaffold
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.compose.components.VFilesListDefaults

/**
 * Storage management is a single ordered collection, not groups sorted by protocol.
 * The order in Settings.STORAGES is authoritative and Storages.move remains the sole writer.
 * Per-row move controls work equally by touch, D-pad, keyboard, TalkBack and switch access;
 * no drag-only affordance or hidden long-press operation is required.
 */
class StorageListFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                val storages by Settings.STORAGES.observeCompose()
                val context = LocalContext.current
                val addTitle = context.getString(R.string.storage_add_storage_title)
                VFilesScreenScaffold(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    title = getString(R.string.storage_list_title),
                    onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (storages.isEmpty()) {
                            VFilesMessageState(
                                title = getString(R.string.storage_list_empty),
                                detail = addTitle,
                                actionLabel = addTitle,
                                onAction = { addStorage() },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(storages, key = { _, storage -> storage.id }) {
                                    index, storage ->
                                    SegmentedListItem(
                                        modifier = Modifier.fillMaxWidth().widthIn(max = 850.dp),
                                        shapes = ListItemDefaults.segmentedShapes(index, storages.size),
                                        colors = VFilesListDefaults.segmentedColors(),
                                        onClick = { startActivitySafe(storage.createEditIntent()) },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(storage.iconRes),
                                                contentDescription = null
                                            )
                                        },
                                        content = {
                                            Text(
                                                storage.getName(context),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (storage.isVisible)
                                                    MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                storage.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingContent = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val name = storage.getName(context)
                                                IconButton(
                                                    enabled = index > 0,
                                                    onClick = { Storages.move(index, index - 1) },
                                                    modifier = Modifier.semantics {
                                                        contentDescription = context.getString(R.string.storage_move_up_named_format, name)
                                                    }
                                                ) { Text("↑", style = MaterialTheme.typography.titleLarge) }
                                                IconButton(
                                                    enabled = index < storages.lastIndex,
                                                    onClick = { Storages.move(index, index + 1) },
                                                    modifier = Modifier.semantics {
                                                        contentDescription = context.getString(R.string.storage_move_down_named_format, name)
                                                    }
                                                ) { Text("↓", style = MaterialTheme.typography.titleLarge) }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (storages.isNotEmpty()) FloatingActionButton(
                            onClick = { addStorage() },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
                                .semantics { contentDescription = addTitle }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.file_browser_icon_add),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addStorage() {
        startActivitySafe(AddStorageDialogActivity::class.createIntent())
    }
}
