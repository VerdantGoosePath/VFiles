/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.asFileNameOrNull

abstract class FileNameDialogFragment : NameDialogFragment() {
    override val listener: Listener
        get() = super.listener as Listener

    override val labelRes: Int = R.string.file_browser_dialog_name_label

    override fun validateName(name: String): String? = when {
        name.isEmpty() -> getString(R.string.file_name_error_empty)
        name.asFileNameOrNull() == null -> getString(R.string.file_name_error_invalid)
        listener.hasFileWithName(name) -> getString(R.string.file_name_error_already_exists)
        else -> null
    }

    interface Listener : NameDialogFragment.Listener {
        fun hasFileWithName(name: String): Boolean
    }
}
