package me.zhanghai.android.files.compose.components

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compose.theme.VFilesTheme

/**
 * A retained [AppCompatDialogFragment] whose dialog body is Compose. Fragment ownership keeps
 * the existing parent-fragment listener contracts and rotation behaviour, while the content uses
 * the same Material 3 Expressive theme as the rest of the app instead of MDC View dialogs.
 */
abstract class ComposeDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AppCompatDialog(requireContext(), R.style.ThemeOverlay_VFiles_ComposeDialog).apply {
            setContentView(
                ComposeView(context).apply {
                    // rememberSaveable keys its registry by view id.
                    id = R.id.composeDialogContent
                    setContent { VFilesTheme { DialogContent() } }
                }
            )
            onConfigureWindow(window!!)
        }

    override fun onStart() {
        super.onStart()
        // The composable centres and sizes the card itself; the window spans the width so the
        // margins around the card are part of the dialog and handled as outside taps.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    protected open fun onConfigureWindow(window: Window) {}

    @Composable
    protected abstract fun DialogContent()
}

/**
 * Material 3 basic dialog layout ([C22]): optional icon, headline, scrollable body and
 * end-aligned text actions that wrap instead of clipping at large font scales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VFilesDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    dismissOnOutsideTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(dismissOnOutsideTap) {
                if (dismissOnOutsideTap) detectTapGestures { onDismissRequest() }
            }
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = modifier.widthIn(min = 280.dp, max = 560.dp)
                // Swallow taps on the card so only the margins count as outside.
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = title },
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(Modifier.padding(24.dp)) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                        ) { icon() }
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                        .then(if (icon != null) Modifier.align(Alignment.CenterHorizontally) else Modifier)
                )
                CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            content = content
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
