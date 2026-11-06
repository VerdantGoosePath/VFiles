package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.settings.FileOpenAppCategory
import me.zhanghai.android.files.settings.FileOpenDefaultApps

/**
 * Set a user-chosen external package only when it actually handles the content:// MIME contract.
 * Used for file shortcuts and staged archive files as well as normal file-list opening.
 */
fun Intent.applyExternalDefaultOpenApp(mimeType: MimeType, context: Context): Intent {
    val category = FileOpenAppCategory.forMimeType(mimeType) ?: return this
    val packageName = FileOpenDefaultApps.getPackage(category) ?: return this
    if (Intent(this).setPackage(packageName).resolveActivity(context.packageManager) != null) {
        setPackage(packageName)
    }
    return this
}
