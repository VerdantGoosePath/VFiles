package me.zhanghai.android.files.ftpserver

import android.app.StatusBarManager
import android.content.ClipData
import android.os.PersistableBundle
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.getSystemServiceCompat
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.compose.theme.observeCompose
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.settings.ExpressiveSettingRow
import me.zhanghai.android.files.settings.ExpressiveSettingsSection
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.requestAddTileService
import me.zhanghai.android.files.util.showToast

/**
 * FTP control and settings share a lifecycle-aware Compose screen. A start/stop switch retains
 * focus through STARTING/STOPPING, but ignores clicks while the service is transitioning. The
 * credential inputs remain hidden when anonymous login is on and while the server is active.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveFtpServerScreen(
    onNavigateUp: () -> Unit,
    onPickHomeDirectory: () -> Unit
) {
    val context = LocalContext.current
    val state by FtpServerService.stateLiveData.observeAsState(FtpServerService.State.STOPPED)
    val anonymous by Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeCompose()
    val username by Settings.FTP_SERVER_USERNAME.observeCompose()
    val password by Settings.FTP_SERVER_PASSWORD.observeCompose()
    val port by Settings.FTP_SERVER_PORT.observeCompose()
    val homeDirectory by Settings.FTP_SERVER_HOME_DIRECTORY.observeCompose()
    val writable by Settings.FTP_SERVER_WRITABLE.observeCompose()

    // FTP URL depends on connectivity, which is independent of preference changes.
    var networkVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = FtpServerUrl.createChangeReceiver(context) { networkVersion++ }
        receiver.register()
        onDispose { receiver.unregister() }
    }
    val url = remember(context, anonymous, username, port, networkVersion) {
        FtpServerUrl.getUrl()
    }

    val running = state == FtpServerService.State.STARTING
        || state == FtpServerService.State.RUNNING
    val configuring = state == FtpServerService.State.STOPPED
    val stateSummary = when (state) {
        FtpServerService.State.STARTING -> stringResource(R.string.ftp_server_state_summary_starting)
        FtpServerService.State.RUNNING -> stringResource(R.string.ftp_server_state_summary_running)
        FtpServerService.State.STOPPING -> stringResource(R.string.ftp_server_state_summary_stopping)
        FtpServerService.State.STOPPED -> stringResource(R.string.ftp_server_state_summary_stopped)
    }

    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.ftp_server_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.settings_expressive_back_24dp),
                            contentDescription = stringResource(R.string.settings_expressive_back)
                        )
                    }
                },
                scrollBehavior = scroll
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 32.dp
            )
        ) {
            item(key = "server") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.ftp_server_title),
                        buildList {
                            add(ExpressiveSettingRow(
                                key = "state",
                                title = context.getString(R.string.ftp_server_state_title),
                                summary = stateSummary,
                                checked = running,
                                iconRes = R.drawable.ftp_server_icon_white_24dp,
                                onClick = {
                                    if (state == FtpServerService.State.RUNNING
                                        || state == FtpServerService.State.STOPPED) {
                                        FtpServerService.toggle(context)
                                    }
                                }
                            ))
                            add(ExpressiveSettingRow(
                                key = "url",
                                title = context.getString(R.string.ftp_server_url_title),
                                summary = url ?: context.getString(
                                    R.string.ftp_server_url_summary_no_local_inet_address
                                ),
                                value = if (url != null) context.getString(R.string.ftp_server_url_menu_copy_url)
                                    else null,
                                enabled = url != null,
                                onClick = { url?.let { clipboardManager.copyText(it, context) } }
                            ))
                            if (!anonymous && password.isNotEmpty()) {
                                add(ExpressiveSettingRow(
                                    key = "copy_password",
                                    title = context.getString(R.string.ftp_server_url_menu_copy_password),
                                    summary = context.getString(R.string.ftp_server_expressive_password_clipboard),
                                    onClick = { copySensitiveFtpPassword(context, password) }
                                ))
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(ExpressiveSettingRow(
                                    key = "tile",
                                    title = context.getString(R.string.ftp_server_add_tile_title),
                                    onClick = { requestFtpQuickSettingsTile(context) }
                                ))
                            }
                        }
                    )
                }
            }
            item(key = "configuration") {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ExpressiveSettingsSection(
                        stringResource(R.string.ftp_server_configuration_title),
                        listOf(
                            ExpressiveSettingRow(
                                "anonymous",
                                context.getString(R.string.ftp_server_anonymous_login_title),
                                checked = anonymous, enabled = configuring,
                                onClick = {
                                    if (configuring) Settings.FTP_SERVER_ANONYMOUS_LOGIN.putValue(!anonymous)
                                }
                            ),
                            ExpressiveSettingRow(
                                "username", context.getString(R.string.ftp_server_username_title),
                                value = if (!anonymous) username else null,
                                enabled = configuring && !anonymous,
                                onClick = { if (configuring && !anonymous) editKey = "username" }
                            ),
                            ExpressiveSettingRow(
                                "password", context.getString(R.string.ftp_server_password_title),
                                // Never place a password, password length, or password characters
                                // in a summary or accessibility tree.
                                summary = context.getString(
                                    if (password.isEmpty()) R.string.ftp_server_expressive_password_unset
                                    else R.string.ftp_server_expressive_password_set
                                ),
                                enabled = configuring && !anonymous,
                                onClick = { if (configuring && !anonymous) editKey = "password" }
                            ),
                            ExpressiveSettingRow(
                                "port", context.getString(R.string.ftp_server_port_title),
                                value = port.toString(),
                                enabled = configuring,
                                onClick = { if (configuring) editKey = "port" }
                            ),
                            ExpressiveSettingRow(
                                "directory", context.getString(R.string.ftp_server_home_directory_title),
                                summary = homeDirectory.toUserFriendlyString(),
                                enabled = configuring,
                                onClick = { if (configuring) onPickHomeDirectory() }
                            ),
                            ExpressiveSettingRow(
                                "writable", context.getString(R.string.ftp_server_writable_title),
                                checked = writable, enabled = configuring,
                                onClick = {
                                    if (configuring) Settings.FTP_SERVER_WRITABLE.putValue(!writable)
                                }
                            )
                        )
                    )
                }
            }
        }
    }

    editKey?.let { selected ->
        val initial = when (selected) {
            "username" -> username
            "password" -> password
            "port" -> port.toString()
            else -> ""
        }
        val title = when (selected) {
            "username" -> stringResource(R.string.ftp_server_username_title)
            "password" -> stringResource(R.string.ftp_server_password_title)
            else -> stringResource(R.string.ftp_server_port_title)
        }
        FtpScalarEditDialog(
            title = title,
            initial = initial,
            secret = selected == "password",
            numeric = selected == "port",
            onDismiss = { editKey = null },
            onSave = { newValue ->
                if (configuring) {
                    when (selected) {
                        "username" -> Settings.FTP_SERVER_USERNAME.putValue(
                            newValue.ifEmpty {
                                context.getString(R.string.pref_default_value_ftp_server_username)
                            }
                        )
                        "password" -> Settings.FTP_SERVER_PASSWORD.putValue(newValue)
                        "port" -> parseFtpListeningPort(newValue)?.let {
                            Settings.FTP_SERVER_PORT.putValue(it)
                        }
                    }
                }
                editKey = null
            }
        )
    }
}

/** FTP binds a concrete TCP port; zero and values above 65535 are invalid here. */
internal fun parseFtpListeningPort(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in 1..65535 }

@Composable
private fun FtpScalarEditDialog(
    title: String,
    initial: String,
    secret: Boolean,
    numeric: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    // Do not save transient password text in SavedStateRegistry / rememberSaveable.
    // Save ordinary port/username drafts on rotation, but never persist typed passwords.
    val inputState = if (secret) remember(title) { mutableStateOf(initial) }
        else rememberSaveable(title) { mutableStateOf(initial) }
    val input by inputState
    val valid = !numeric || parseFtpListeningPort(input) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { inputState.value = it },
                    label = { Text(title) },
                    isError = !valid,
                    singleLine = true,
                    visualTransformation = if (secret) PasswordVisualTransformation()
                        else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            secret -> KeyboardType.Password
                            numeric -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) {
                    Text(
                        stringResource(R.string.ftp_server_expressive_invalid_port),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }, enabled = valid) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

private fun requestFtpQuickSettingsTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val statusBarManager = context.getSystemServiceCompat(StatusBarManager::class.java)
    statusBarManager.requestAddTileService(
        FtpServerTileService::class.java, context.mainExecutorCompat
    ) { result ->
        val resultRes = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> return@requestAddTileService
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                R.string.ftp_server_add_tile_result_already_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                R.string.ftp_server_add_tile_result_added
            else -> R.string.ftp_server_add_tile_result_error
        }
        context.showToast(resultRes)
    }
}

/** Never pass a password through copyText(): it repeats clipboard contents in a Toast. */
private fun copySensitiveFtpPassword(context: Context, password: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.ftp_server_password_title), password)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // Also suppress Android 13+ system clipboard-preview plaintext. The documented key
        // works on pre-33 versions where ClipDescription.EXTRA_IS_SENSITIVE is not defined.
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboardManager.setPrimaryClip(clip)
    context.showToast(R.string.ftp_server_expressive_password_copied)
}
