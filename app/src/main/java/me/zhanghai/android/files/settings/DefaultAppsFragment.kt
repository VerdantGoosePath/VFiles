package me.zhanghai.android.files.settings

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose

/** Only EXTERNAL application choices; never offers internal image/text/editor routes. */
class DefaultAppsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                ExternalDefaultAppsScreen(onNavigateUp = { requireActivity().finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalDefaultAppsScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val saved by Settings.FILE_OPEN_DEFAULT_APPS.observeCompose()
    val owner = LocalLifecycleOwner.current
    var resumeVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var picked by remember { mutableStateOf<FileOpenAppCategory?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_default_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_expressive_back))
                    }
                },
                scrollBehavior = scroll
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.settings_expressive_external_apps),
                        FileOpenAppCategory.entries.map { category ->
                            val chosenPackage = saved.firstOrNull { it.category == category.name }?.packageName
                            val appName = chosenPackage?.let { appLabel(context.packageManager, it) }
                            ExpressiveSettingRow(
                                key = category.name,
                                title = context.getString(category.titleRes),
                                summary = appName ?: context.getString(R.string.settings_default_apps_ask),
                                onClick = { picked = category }
                            )
                        }
                    )
                }
            }
        }
    }

    picked?.let { category ->
        val candidates = remember(category, resumeVersion) {
            // The real ACTION_VIEW uses a content: FileProvider URI and MIME type.
            // Querying a type-only intent accidentally excludes scheme-qualified viewers.
            val uri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.FILE_PROVIDIER_AUTHORITY).build()
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, category.queryMimeType), 0
            ).map { it.activityInfo.packageName }.distinct()
                .mapNotNull { package ->
                    appLabel(context.packageManager, package)?.let { package to it }
                }.sortedBy { it.second.lowercase() }
        }
        val choices: List<Pair<String?, String>> =
            listOf(null to context.getString(R.string.settings_default_apps_ask)) +
                candidates.map { it.first as String? to it.second }
        ExpressiveChoiceDialog(
            title = context.getString(category.titleRes),
            options = choices,
            selected = FileOpenDefaultApps.getPackage(category),
            onSelected = { packageName -> FileOpenDefaultApps.setPackage(category, packageName) },
            onDismiss = { picked = null }
        )
    }
}

private fun appLabel(pm: PackageManager, packageName: String): String? =
    try {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
