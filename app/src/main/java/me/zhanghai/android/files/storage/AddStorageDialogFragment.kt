/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.compose.components.PreferenceRowUi
import me.zhanghai.android.files.compose.components.VFilesPreferenceGroup
import me.zhanghai.android.files.compose.theme.VFilesTheme
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.asExternalStorageUri
import me.zhanghai.android.files.provider.document.resolver.ExternalStorageProviderHacks
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

class AddStorageDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // The existing activity and Intent contracts remain unchanged: this is only the chooser
        // surface. Each action still launches the original SAF/remote-connection workflow.
        val options = STORAGE_TYPES
        val chooser = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                VFilesTheme {
                    Column(
                        modifier = Modifier.widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VFilesPreferenceGroup(
                            title = getString(R.string.storage_add_storage_title),
                            rows = options.mapIndexed { index, option ->
                                PreferenceRowUi(
                                    key = index.toString(),
                                    title = getString(option.first)
                                )
                            },
                            onSelect = { key ->
                                val selected = key.toIntOrNull()?.let(options::getOrNull)
                                if (selected != null) {
                                    startActivitySafe(selected.second)
                                    finish()
                                }
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text(getString(android.R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(chooser)
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    companion object {
        private val STORAGE_TYPES = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.storage_add_storage_android_data to
                    AddExternalStorageShortcutActivity ::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_data,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA
                                .asExternalStorageUri()
                        )
                    )
            } else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.storage_add_storage_android_obb to
                    AddExternalStorageShortcutActivity ::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_obb,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB
                                .asExternalStorageUri()
                        )
                    )
            } else null,
            R.string.storage_add_storage_document_tree to
                AddDocumentTreeActivity::class.createIntent(),
            R.string.storage_add_storage_ftp_server to
                EditFtpServerActivity::class.createIntent().putArgs(EditFtpServerFragment.Args()),
            R.string.storage_add_storage_sftp_server to
                EditSftpServerActivity::class.createIntent().putArgs(EditSftpServerFragment.Args()),
            R.string.storage_add_storage_smb_server to
                AddLanSmbServerActivity::class.createIntent(),
            R.string.storage_add_storage_webdav_server to
                EditWebDavServerActivity::class.createIntent()
                    .putArgs(EditWebDavServerFragment.Args()),
        )
    }
}
