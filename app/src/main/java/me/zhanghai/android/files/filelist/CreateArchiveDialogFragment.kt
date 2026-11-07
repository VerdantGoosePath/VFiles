/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.libarchive.Archive

class CreateArchiveDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()

    override val listener: Listener
        get() = super.listener as Listener

    // Fragment-level state: transformName() and onOk() read it outside composition.
    private var type by mutableStateOf(ArchiveType.ZIP)
    private var password by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = ArchiveType.fromId(
            savedInstanceState?.getInt(STATE_TYPE) ?: Settings.CREATE_ARCHIVE_TYPE.valueCompat
        )
        password = savedInstanceState?.getString(STATE_PASSWORD).orEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TYPE, type.id)
        outState.putString(STATE_PASSWORD, password)
    }

    @StringRes
    override val titleRes: Int = R.string.file_create_archive_title

    override val initialName: String?
        get() {
            val files = args.files
            if (files.size == 1) {
                return files.single().path.fileName.toString()
            }
            val parent = files.mapTo(mutableSetOf()) { it.path.parent }.singleOrNull()
            return if (parent != null && parent.nameCount > 0) parent.fileName.toString() else null
        }

    override fun transformName(text: String): String = "$text.${type.extension}"

    override fun onOk(name: String) {
        val password = if (type.supportsPassword) password.takeIfNotEmpty() else null
        listener.archive(args.files, name, type.format, type.filter, password)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun ColumnScope.ExtraContent() {
        Text(
            stringResource(R.string.file_create_archive_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() }
        )
        val types = ArchiveType.entries
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            types.forEachIndexed { index, entry ->
                ToggleButton(
                    checked = type == entry,
                    onCheckedChange = {
                        type = entry
                        Settings.CREATE_ARCHIVE_TYPE.putValue(entry.id)
                    },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
                ) {
                    Text(stringResource(entry.labelRes))
                }
            }
        }
        AnimatedVisibility(type.supportsPassword) {
            var visible by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.file_create_archive_password)) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, autoCorrectEnabled = false
                ),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            painterResource(
                                if (visible) R.drawable.file_browser_icon_visibility_off
                                else R.drawable.file_browser_icon_visibility
                            ),
                            contentDescription = stringResource(
                                if (visible) R.string.file_browser_password_hide
                                else R.string.file_browser_password_show
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }

    private enum class ArchiveType(
        val id: Int,
        @StringRes val labelRes: Int,
        val extension: String,
        val format: Int,
        val filter: Int,
        val supportsPassword: Boolean
    ) {
        ZIP(
            R.id.zipRadio, R.string.file_create_archive_type_zip, "zip", Archive.FORMAT_ZIP,
            Archive.FILTER_NONE, true
        ),
        TAR_XZ(
            R.id.tarXzRadio, R.string.file_create_archive_type_tar_xz, "tar.xz",
            Archive.FORMAT_TAR, Archive.FILTER_XZ, false
        ),
        SEVEN_Z(
            R.id.sevenZRadio, R.string.file_create_archive_type_7z, "7z", Archive.FORMAT_7ZIP,
            Archive.FILTER_NONE, false
        );

        companion object {
            fun fromId(id: Int): ArchiveType = entries.firstOrNull { it.id == id } ?: ZIP
        }
    }

    companion object {
        private const val STATE_TYPE = "type"
        private const val STATE_PASSWORD = "password"

        fun show(files: FileItemSet, fragment: Fragment) {
            CreateArchiveDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: FileItemSet) : ParcelableArgs

    interface Listener : FileNameDialogFragment.Listener {
        fun archive(files: FileItemSet, name: String, format: Int, filter: Int, password: String?)
    }
}
