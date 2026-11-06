/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */
package me.zhanghai.android.files.ftpserver

import android.app.StatusBarManager
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.getSystemServiceCompat
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.compose.components.VFilesScreenScaffold
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.RuntimeBroadcastReceiver
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.requestAddTileService
import me.zhanghai.android.files.util.showToast

/**
 * Expressive FTP server control centre. Observes the existing LiveData-backed settings and
 * service; never creates a second source of truth for saved preferences or connection state.
 * Configuration is locked while the service is starting/running/stopping, as on the old screen.
 */
class FtpServerFragment : Fragment() {
    private val folderPicker = registerForActivityResult(
        FileListActivity.OpenDirectoryContract()
    ) { path: Path? ->
        path?.let { Settings.FTP_SERVER_HOME_DIRECTORY.putValue(it) }
    }

    private var urlRevision by mutableIntStateOf(0)
    private var networkReceiver: RuntimeBroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkReceiver = FtpServerUrl.createChangeReceiver(requireContext()) {
            urlRevision++
        }
    }

    override fun onStart() {
        super.onStart()
        networkReceiver?.register()
    }

    override fun onStop() {
        networkReceiver?.unregister()
        super.onStop()
    }

    override fun onDestroy() {
        networkReceiver = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VFilesTheme {
                val state by FtpServerService.stateLiveData.observeAsState(FtpServerService.State.STOPPED)
                val anonymous by Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeCompose()
                val user by Settings.FTP_SERVER_USERNAME.observeCompose()
                val password by Settings.FTP_SERVER_PASSWORD.observeCompose()
                val port by Settings.FTP_SERVER_PORT.observeCompose()
                val home by Settings.FTP_SERVER_HOME_DIRECTORY.observeCompose()
                val writable by Settings.FTP_SERVER_WRITABLE.observeCompose()
                var editing by remember { mutableStateOf<SettingEditor?>(null) }

                val busy = state == FtpServerService.State.STARTING ||
                    state == FtpServerService.State.STOPPING
                val running = state == FtpServerService.State.RUNNING
                val editable = !running && !busy
                val summary = when (state) {
                    FtpServerService.State.STARTING -> R.string.ftp_server_state_summary_starting
                    FtpServerService.State.RUNNING -> R.string.ftp_server_state_summary_running
                    FtpServerService.State.STOPPING -> R.string.ftp_server_state_summary_stopping
                    FtpServerService.State.STOPPED -> R.string.ftp_server_state_summary_stopped
                }
                // Revision is changed by the registered network receiver, so this value refreshes
                // on Wi-Fi changes as well as username/port changes.
                @Suppress("UNUSED_VARIABLE")
                val refresh = urlRevision
                val address = FtpServerUrl.getUrl()
                VFilesScreenScaffold(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    title = getString(R.string.ftp_server_title),
                    onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (running) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                getString(R.string.ftp_server_state_title),
                                                style = MaterialTheme.typography.titleLargeEmphasized
                                            )
                                            Text(
                                                getString(summary),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Switch(
                                            checked = running || state == FtpServerService.State.STARTING,
                                            onCheckedChange = {
                                                if (!busy) FtpServerService.toggle(requireContext())
                                            },
                                            enabled = !busy
                                        )
                                    }
                                    if (address != null) {
                                        Text(
                                            address,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        OutlinedButton(onClick = {
                                            clipboardManager.copyText(address, requireContext())
                                        }) {
                                            Text(getString(R.string.ftp_server_url_menu_copy_url))
                                        }
                                    } else {
                                        Text(
                                            getString(R.string.ftp_server_url_summary_no_local_inet_address),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            Text(
                                getString(R.string.ftp_server_configuration_title),
                                modifier = Modifier.padding(start = 16.dp),
                                style = MaterialTheme.typography.titleMediumEmphasized
                            )
                            val entries = listOf(
                                "anonymous", "username", "password", "port", "home", "writable"
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                            ) {
                                entries.forEachIndexed { index, key ->
                                    val enabled = editable && (anonymous.not() ||
                                        key !in setOf("username", "password"))
                                    val title = when (key) {
                                        "anonymous" -> R.string.ftp_server_anonymous_login_title
                                        "username" -> R.string.ftp_server_username_title
                                        "password" -> R.string.ftp_server_password_title
                                        "port" -> R.string.ftp_server_port_title
                                        "home" -> R.string.ftp_server_home_directory_title
                                        else -> R.string.ftp_server_writable_title
                                    }
                                    val supporting = when (key) {
                                        "username" -> user
                                        "password" -> if (password.isNotEmpty()) "••••••••"
                                            else getString(R.string.ftp_server_password_not_set)
                                        "port" -> port.toString()
                                        "home" -> home.toUserFriendlyString()
                                        else -> null
                                    }
                                    SegmentedListItem(
                                        onClick = {
                                            when (key) {
                                                "anonymous" ->
                                                    Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(!anonymous)
                                                "writable" ->
                                                    Settings.FTP_SERVER_WRITABLE.putValue(!writable)
                                                "home" ->
                                                    folderPicker.launchSafe(home, this@FtpServerFragment)
                                                "username" -> editing = SettingEditor.Username
                                                "password" -> editing = SettingEditor.Password
                                                "port" -> editing = SettingEditor.Port
                                            }
                                        },
                                        enabled = enabled,
                                        shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                                        content = {
                                            Text(
                                                getString(title),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        },
                                        supportingContent = supporting?.let { info ->
                                            {
                                                Text(
                                                    info,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingContent = when (key) {
                                            "anonymous" -> {
                                                { Switch(checked = anonymous, onCheckedChange = null) }
                                            }
                                            "writable" -> {
                                                { Switch(checked = writable, onCheckedChange = null) }
                                            }
                                            else -> null
                                        }
                                    )
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                resources.getBoolean(R.bool.pref_visible_ftp_server_add_tile)
                            ) {
                                OutlinedButton(
                                    onClick = { requestTile() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(getString(R.string.ftp_server_add_tile_title))
                                }
                            }
                        }
                    }
                }
                val editor = editing
                if (editor != null) {
                    EditServerSettingDialog(
                        editor = editor,
                        initial = when (editor) {
                            SettingEditor.Username -> user
                            SettingEditor.Password -> password
                            SettingEditor.Port -> port.toString()
                        },
                        onDismiss = { editing = null },
                        onSave = { text ->
                            when (editor) {
                                SettingEditor.Username ->
                                    Settings.FTP_SERVER_USERNAME.putValue(
                                        text.ifBlank { getString(R.string.pref_default_value_ftp_server_username) }
                                    )
                                SettingEditor.Password -> Settings.FTP_SERVER_PASSWORD.putValue(text)
                                SettingEditor.Port ->
                                    Settings.FTP_SERVER_PORT.putValue(text.toInt())
                            }
                            editing = null
                        }
                    )
                }
            }
        }
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        requireContext().getSystemServiceCompat(StatusBarManager::class.java)
            .requestAddTileService(
                FtpServerTileService::class.java, requireContext().mainExecutorCompat
            ) { result ->
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> return@requestAddTileService
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        R.string.ftp_server_add_tile_result_already_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        R.string.ftp_server_add_tile_result_added
                    else -> R.string.ftp_server_add_tile_result_error
                }
                context?.showToast(message)
            }
    }

    enum class SettingEditor { Username, Password, Port }
}

@Composable
private fun EditServerSettingDialog(
    editor: FtpServerFragment.SettingEditor,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable(editor) { mutableStateOf(initial) }
    val isPort = editor == FtpServerFragment.SettingEditor.Port
    val valid = if (isPort) value.toIntOrNull() in 1..65535 else true
    val title = when (editor) {
        FtpServerFragment.SettingEditor.Username -> R.string.ftp_server_username_title
        FtpServerFragment.SettingEditor.Password -> R.string.ftp_server_password_title
        FtpServerFragment.SettingEditor.Port -> R.string.ftp_server_port_title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(androidx.compose.ui.res.stringResource(title)) },
                visualTransformation = if (editor == FtpServerFragment.SettingEditor.Password)
                    PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPort) KeyboardType.Number
                        else if (editor == FtpServerFragment.SettingEditor.Password)
                            KeyboardType.Password else KeyboardType.Text
                ),
                isError = !valid,
                supportingText = if (!valid) {
                    { Text(androidx.compose.ui.res.stringResource(R.string.ftp_server_port_range_error)) }
                } else null
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = valid) {
                Text(androidx.compose.ui.res.stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        }
    )
}
