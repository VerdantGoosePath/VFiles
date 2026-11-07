package me.zhanghai.android.files.ui

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment

/**
 * Material 3 View dialogs are still the correct native primitive for short confirmations.
 * Highlight the irreversible action using the *current theme's paired error role*, not an
 * unrelated hard-coded primary accent or an icon-only affordance.
 */
fun AppCompatDialogFragment.tintDestructivePositiveButton() {
    val alert = dialog as? AlertDialog ?: return
    val styled = requireContext().obtainStyledAttributes(
        intArrayOf(android.R.attr.colorError)
    )
    // API 26+ framework error role where available; older themed hosts use the
    // Material error fallback rather than a primary-coloured destructive action.
    val errorColor = styled.getColor(0, 0xFFB3261E.toInt())
    styled.recycle()
    alert.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(errorColor)
}
