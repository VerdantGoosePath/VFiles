/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import me.zhanghai.android.files.compose.components.ComposeDialogFragment
import me.zhanghai.android.files.compose.components.VFilesDialog

/**
 * Single-field naming dialog (create, rename, navigate to path, archive name). Validation stays
 * in the subclasses; the field shows their error inline and the dialog only closes on success.
 */
abstract class NameDialogFragment : ComposeDialogFragment() {
    protected open val listener: Listener
        get() = requireParentFragment() as Listener

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val labelRes: Int

    protected open val initialName: String? = null

    /** End of the initially selected range, e.g. the base name before a file's extension. */
    protected open val initialSelectionEnd: Int
        get() = initialName?.length ?: 0

    /** Maps the trimmed field text to the final name, e.g. appending an archive extension. */
    protected open fun transformName(text: String): String = text

    /** Returns an error message, or null when [name] is acceptable. */
    protected open fun validateName(name: String): String? = null

    protected abstract fun onOk(name: String)

    /** Additional controls below the name field. */
    @Composable
    protected open fun ColumnScope.ExtraContent() {}

    override fun onConfigureWindow(window: Window) {
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    private fun submit(text: String): String? {
        val name = transformName(text.trim())
        if (name == initialName) {
            dismiss()
            return null
        }
        val error = validateName(name)
        if (error == null) {
            onOk(name)
            dismiss()
        }
        return error
    }

    @Composable
    override fun DialogContent() {
        var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val initial = initialName.orEmpty()
            mutableStateOf(
                TextFieldValue(initial, TextRange(0, initialSelectionEnd.coerceIn(0, initial.length)))
            )
        }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val onConfirm = { error = submit(value.text) }
        VFilesDialog(
            title = stringResource(titleRes),
            onDismissRequest = ::dismiss,
            confirmButton = {
                TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                label = { Text(stringResource(labelRes)) },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onConfirm() }
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            ExtraContent()
        }
    }

    interface Listener
}
