package me.zhanghai.android.files.compose.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Central spacing constraints for C/D/E; widths follow actual window size. */
object VFilesSpacing {
    val gutter = 16.dp
    val section = 24.dp
    val item = 8.dp
    val contentMaxWidth = 840.dp
}

/**
 * Segmented list colours. The platform token paints segments with `surface`, which disappears on
 * the app's `surface` window background, so groups are lifted one tonal step instead.
 */
object VFilesListDefaults {
    @Composable
    fun segmentedColors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer
    ): ListItemColors = ListItemDefaults.segmentedColors(
        containerColor = containerColor,
        disabledContainerColor = containerColor
    )

    /** For segmented groups hosted on a bottom sheet (`surfaceContainerLow`). */
    @Composable
    fun sheetSegmentedColors(): ListItemColors =
        segmentedColors(MaterialTheme.colorScheme.surfaceContainerHigh)
}

/**
 * Insets boundary for a ComposeView in an AppCompat View hierarchy.
 * The View host handles status/nav insets; Scaffold gets zero platform insets to avoid doubling
 * legacy PersistentBarLayout raw WindowInsets. Full-screen Compose owners choose own insets.
 * [content] owns innerPadding and consumes it once (LazyColumn: contentPadding).
 */
@Composable
fun VFilesScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.settings_expressive_back_24dp),
                                contentDescription = stringResource(R.string.settings_expressive_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        content = content
    )
}

/** Indeterminate short wait vs determinate real file byte progress. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VFilesFileProgress(
    title: String,
    transferredBytes: Long?,
    totalBytes: Long?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val progress = if (transferredBytes != null && totalBytes != null && totalBytes > 0L) {
        (transferredBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else null
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (progress == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            val percent = (progress * 100).toInt()
            Text(
                "$transferredBytes / $totalBytes bytes ($percent%)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (onCancel != null) OutlinedButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
    }
}

/** Calm accessible empty/permission/error state with one recovery action. */
@Composable
fun VFilesMessageState(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth().padding(VFilesSpacing.section),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Hoisted confirmation: callers own show/confirm/delete state. */
@Composable
fun VFilesConfirmation(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/** Remote protocol forms own credential/security validation upstream, not in this field. */
@Composable
fun VFilesRemoteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = true,
        enabled = enabled, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supporting?.let { { Text(it) } }
    )
}
