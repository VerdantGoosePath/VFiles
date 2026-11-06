/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.settings

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.PreferenceRowUi
import me.zhanghai.android.files.compose.components.VFilesPreferenceGroup
import me.zhanghai.android.files.compose.components.VFilesScreenScaffold
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose

/**
 * External default-app preferences are rendered in expressive grouped rows. App resolution keeps
 * the exact same content-scheme query contract used by the file list, and persists only a package.
 * A removed application is displayed as "Ask every time", never forced into an invalid intent.
 */
class DefaultAppsFragment : Fragment() {
    private var installedAppsRevision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        installedAppsRevision++
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                // Recompose immediately after another dialog changes the SharedPreferences setting.
                val stored by Settings.FILE_OPEN_DEFAULT_APPS.observeCompose()
                val revision = installedAppsRevision
                var selected by remember { mutableStateOf<FileOpenAppCategory?>(null) }
                val categories = FileOpenAppCategory.entries
                VFilesScreenScaffold(
                    title = getString(R.string.settings_default_apps_title),
                    onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(innerPadding)
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VFilesPreferenceGroup(
                            title = getString(R.string.settings_default_apps_title),
                            modifier = Modifier.widthIn(max = 720.dp),
                            rows = categories.map { category ->
                                val packageName = stored.firstOrNull {
                                    it.category == category.name
                                }?.packageName
                                PreferenceRowUi(
                                    key = category.name,
                                    title = getString(category.titleRes),
                                    supporting = packageName?.let(::applicationLabel)
                                        ?: getString(R.string.settings_default_apps_ask)
                                )
                            },
                            onSelect = { key ->
                                selected = categories.firstOrNull { it.name == key }
                            }
                        )
                    }
                }
                selected?.let { category ->
                    val candidates = remember(category, revision) { resolveApps(category) }
                    val activePackage = stored.firstOrNull {
                        it.category == category.name
                    }?.packageName
                    AppSelectionDialog(
                        title = getString(category.titleRes),
                        askLabel = getString(R.string.settings_default_apps_ask),
                        candidates = candidates,
                        activePackage = activePackage,
                        onSelect = { packageName ->
                            FileOpenDefaultApps.setPackage(category, packageName)
                            selected = null
                        },
                        onDismiss = { selected = null }
                    )
                }
            }
        }
    }

    private fun applicationLabel(packageName: String): String? =
        try {
            val pm = requireContext().packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun resolveApps(category: FileOpenAppCategory): List<Pair<String, String>> {
        val pm = requireContext().packageManager
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.FILE_PROVIDIER_AUTHORITY)
            .build()
        val query = Intent(Intent.ACTION_VIEW).setDataAndType(uri, category.queryMimeType)
        return pm.queryIntentActivities(query, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg -> applicationLabel(pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
    }
}

@Composable
private fun AppSelectionDialog(
    title: String,
    askLabel: String,
    candidates: List<Pair<String, String>>,
    activePackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = listOf(null to askLabel) + candidates.map { it.first to it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                itemsIndexed(rows, key = { index, item -> item.first ?: "ask-$index" }) { index, item ->
                    SegmentedListItem(
                        onClick = { onSelect(item.first) },
                        shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                        content = { Text(item.second) },
                        trailingContent = {
                            RadioButton(
                                selected = item.first == activePackage,
                                onClick = null
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
