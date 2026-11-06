package me.zhanghai.android.files.storage

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

/**
 * Shared destructive decision for storage entries. Do not remove URI grants or remote credentials
 * until the user explicitly confirms; actual removal is still delegated to Storages.remove().
 */
internal fun Fragment.confirmRemoveStorage(onConfirmed: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.storage_remove_confirmation_title)
        .setMessage(R.string.storage_remove_confirmation_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.remove) { _, _ -> onConfirmed() }
        .show()
}

/**
 * Connection failures can contain user:password@host URLs, access tokens or private key material.
 * Never log the raw Throwable or expose its full toString() through a Snackbar/Toast/dialog.
 * Keep a short, sanitized cause so users can distinguish TLS, DNS and timeout failures.
 */
internal fun safeRemoteErrorMessage(error: Throwable, secrets: Iterable<String?>): String {
    val root = generateSequence(error) { it.cause }.last()
    var detail = root.localizedMessage?.trim().orEmpty()
    if (detail.isBlank()) detail = root.javaClass.simpleName
    if (detail.length > 320 || detail.contains('\n') || detail.contains('\r')) {
        return "The connection could not be established. Check the address, credentials and network."
    }
    secrets.filterNotNull().filter { it.isNotEmpty() }.forEach { secret ->
        detail = detail.replace(secret, "[redacted]")
    }
    detail = detail.replace(
        Regex("(?i)://[^\\s/@]+@"), "://[redacted]@"
    ).replace(
        Regex("(?i)(password|passphrase|token|secret|private[ _-]?key)\\s*[:=]\\s*[^\\s,;]+"),
        "$1=[redacted]"
    )
    return detail.ifBlank {
        "The connection could not be established. Check the address, credentials and network."
    }
}

internal fun Fragment.showRemoteConnectionError(error: Throwable, vararg secrets: String?) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.storage_connection_failed_title)
        .setMessage(safeRemoteErrorMessage(error, secrets.asList()))
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
