/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.isExternalStorageAccessGranted
import me.zhanghai.android.files.util.supportsExternalStorageManager

/**
 * First run is an adaptive permission checklist rather than an image-only wizard.
 * Android owns all permission dialogs. Optional grants never gate continuation.
 */
class OnboardingActivity : AppActivity() {
    private var finishing = false
    private var permissionRevision by mutableIntStateOf(0)
    private val pages by lazy {
        buildList {
            add(OnboardingPage.FilesAccess)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(OnboardingPage.Notifications)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(OnboardingPage.InstallApps)
            }
        }
    }

    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val requestSystemSettingsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VFilesTheme {
                val revision = permissionRevision
                val statuses = remember(revision) { pages.map { it to it.isGranted(this) } }
                val requiredGranted = statuses.none { (page, granted) ->
                    page.isRequired && !granted
                }
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = ::finishOnboarding,
                                    enabled = requiredGranted,
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                                ) { Text(getString(R.string.onboarding_finish)) }
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                getString(R.string.onboarding_welcome_title, getString(R.string.app_name)),
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                getString(R.string.onboarding_welcome_description),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            statuses.forEach { (page, granted) ->
                                PermissionCard(
                                    page = page,
                                    granted = granted,
                                    onGrant = { page.requestGrant(this@OnboardingActivity) }
                                )
                            }
                        }
                    }
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (!finishing) permissionRevision++
    }

    private fun finishOnboarding() {
        if (finishing || pages.any { it.isRequired && !it.isGranted(this) }) return
        finishing = true
        Settings.ONBOARDING_COMPLETED.putValue(true)
        startActivity(FileListActivity::class.createIntent())
        finish()
    }

    @Composable
    private fun PermissionCard(
        page: OnboardingPage,
        granted: Boolean,
        onGrant: () -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (page.isRequired && !granted)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(page.iconRes),
                        contentDescription = null
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            getString(page.titleRes),
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                        Text(
                            if (granted) getString(R.string.onboarding_granted)
                            else getString(
                                if (page.isRequired) R.string.onboarding_required
                                else R.string.onboarding_optional
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Text(page.getDescription(this@OnboardingActivity),
                    style = MaterialTheme.typography.bodyMedium)
                if (!granted) {
                    Button(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                        Text(getString(R.string.onboarding_grant))
                    }
                }
            }
        }
    }

    fun openStorageSettings() {
        val intent = if (Environment::class.supportsExternalStorageManager()) {
            Environment::class.createManageAppAllFilesAccessPermissionIntent(packageName)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        requestSystemSettingsLauncher.launch(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun openInstallAppsSettings() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        requestSystemSettingsLauncher.launch(intent)
    }

    // ── pages ─────────────────────────────────────────────────────────────────

    sealed class OnboardingPage(
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
        val isRequired: Boolean
    ) {
        open fun getDescription(context: Context): String = context.getString(descriptionRes)

        abstract fun isGranted(context: Context): Boolean
        abstract fun requestGrant(activity: OnboardingActivity)

        object FilesAccess : OnboardingPage(
            iconRes = R.drawable.sd_card_icon_white_24dp,
            titleRes = R.string.onboarding_files_title,
            descriptionRes = R.string.onboarding_files_description,
            isRequired = true
        ) {
            override fun getDescription(context: Context): String =
                context.getString(descriptionRes, context.getString(R.string.app_name))

            override fun isGranted(context: Context): Boolean =
                Environment::class.isExternalStorageAccessGranted()

            override fun requestGrant(activity: OnboardingActivity) {
                activity.openStorageSettings()
            }
        }

        object Notifications : OnboardingPage(
            iconRes = R.drawable.notification_icon_white_24dp,
            titleRes = R.string.onboarding_notifications_title,
            descriptionRes = R.string.onboarding_notifications_description,
            isRequired = false
        ) {
            override fun isGranted(context: Context): Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

            override fun requestGrant(activity: OnboardingActivity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.requestNotificationPermission()
                }
            }
        }

        object InstallApps : OnboardingPage(
            iconRes = R.drawable.install_unknown_apps_icon_white_24dp,
            titleRes = R.string.onboarding_install_apps_title,
            descriptionRes = R.string.onboarding_install_apps_description,
            isRequired = false
        ) {
            override fun getDescription(context: Context): String =
                context.getString(descriptionRes, context.getString(R.string.app_name))

            override fun isGranted(context: Context): Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    true
                }

            override fun requestGrant(activity: OnboardingActivity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.openInstallAppsSettings()
                }
            }
        }
    }
}
