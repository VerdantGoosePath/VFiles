/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.about

import android.net.Uri
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.components.PreferenceRowUi
import me.zhanghai.android.files.compose.components.VFilesPreferenceGroup
import me.zhanghai.android.files.compose.components.VFilesScreenScaffold
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.ui.LicensesDialogFragment
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe

/**
 * Native expressive About page. No preference or external-link semantics are reconstructed from
 * formatted text: each row has its own click target and the existing licenses dialog is reused.
 */
class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                VFilesScreenScaffold(
                    title = getString(R.string.about_title),
                    onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        getString(R.string.app_name),
                                        style = MaterialTheme.typography.headlineMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        getString(R.string.about_version_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        getString(R.string.app_version),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            VFilesPreferenceGroup(
                                title = getString(R.string.about_title),
                                rows = listOf(
                                    PreferenceRowUi("github", getString(R.string.about_github_title)),
                                    PreferenceRowUi("licenses", getString(R.string.about_licenses_title))
                                ),
                                onSelect = { key ->
                                    when (key) {
                                        "github" -> startActivitySafe(GITHUB_URI.createViewIntent())
                                        "licenses" -> LicensesDialogFragment.show(this@AboutFragment)
                                    }
                                }
                            )
                            VFilesPreferenceGroup(
                                title = getString(R.string.about_author_title),
                                rows = listOf(
                                    PreferenceRowUi(
                                        "author", getString(R.string.about_author_name_title),
                                        getString(R.string.about_fork_subtitle)
                                    ),
                                    PreferenceRowUi(
                                        "author_github", getString(R.string.about_author_github_title)
                                    )
                                ),
                                onSelect = { key ->
                                    when (key) {
                                        "author" -> startActivitySafe(AUTHOR_RESUME_URI.createViewIntent())
                                        "author_github" -> startActivitySafe(AUTHOR_GITHUB_URI.createViewIntent())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val GITHUB_URI = Uri.parse("https://github.com/Victor-root/VFiles")
        private val AUTHOR_RESUME_URI = Uri.parse("https://github.com/Victor-root")
        private val AUTHOR_GITHUB_URI = Uri.parse("https://github.com/Victor-root")
    }
}
