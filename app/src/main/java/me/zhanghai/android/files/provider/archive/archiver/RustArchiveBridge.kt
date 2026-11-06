package me.zhanghai.android.files.provider.archive.archiver

import androidx.preference.PreferenceManager
import me.zhanghai.android.files.app.application
import org.json.JSONObject
import java.io.File

/** Names are deliberately independent of libarchive's numeric constants. */
object ArchiveFormats {
    const val ZIP = 1
    const val TAR_XZ = 2
    const val SEVEN_Z = 3
    fun name(format: Int): String = when (format) {
        ZIP -> "zip"
        TAR_XZ -> "tar.xz"
        SEVEN_Z -> "7z"
        else -> throw IllegalArgumentException("Unsupported archive format: $format")
    }
}

/** Shared preference keys are used by the settings screen and both archive dialogs. */
object ArchiveSettings {
    const val ZIP_ENGINE = "archive_zip_engine"
    const val RAWZIP_LEVEL = "archive_rawzip_level"
    const val RIPZIP_LEVEL = "archive_ripzip_level"
    const val SEVENZ_LEVEL = "archive_sevenz_level"
    const val TAR_LEVEL = "archive_tar_level"
    const val RAWZIP_METHOD = "archive_rawzip_method"
    const val RIPZIP_METHOD = "archive_ripzip_method"
    const val SEVENZ_SOLID = "archive_sevenz_solid"
    const val SEVENZ_ENCRYPT_HEADERS = "archive_sevenz_encrypt_headers"
    const val RAR_VERIFY = "archive_rar_verify"
    const val MAX_ENTRY_MIB = "archive_max_entry_mib"

    val preferences get() = PreferenceManager.getDefaultSharedPreferences(application)

    fun zipEngine(): String = preferences.getString(ZIP_ENGINE, "rawzip") ?: "rawzip"

    fun level(format: String, engine: String = zipEngine()): Int {
        val key = when (format) {
            "zip" -> if (engine == "ripzip") RIPZIP_LEVEL else RAWZIP_LEVEL
            "7z" -> SEVENZ_LEVEL
            else -> TAR_LEVEL
        }
        return preferences.getInt(key, if (format == "tar.xz") 6 else 5).coerceIn(0, 9)
    }

    fun optionsFor(format: String, override: String? = null): String {
        val engine = zipEngine()
        val prefs = preferences
        val result = JSONObject()
            .put("zip_engine", engine)
            .put("zip_method", prefs.getString(if (engine == "ripzip") RIPZIP_METHOD else RAWZIP_METHOD, "deflate"))
            .put("level", level(format, engine))
            .put("sevenz_solid", prefs.getBoolean(SEVENZ_SOLID, false))
            .put("sevenz_encrypt_headers", prefs.getBoolean(SEVENZ_ENCRYPT_HEADERS, true))
            .put("rar_verify", prefs.getBoolean(RAR_VERIFY, true))
            .put("max_entry_bytes", (prefs.getString(MAX_ENTRY_MIB, "2048")?.toLongOrNull() ?: 2048L)
                .coerceIn(1, 16384) * 1024 * 1024)
        if (!override.isNullOrBlank()) {
            val overrides = JSONObject(override)
            for (key in overrides.keys()) result.put(key, overrides.get(key))
        }
        return result.toString()
    }
}

/** All native calls operate on private local staging files, not arbitrary Android Path providers. */
object RustArchiveBridge {
    init { System.loadLibrary("vfiles_archive") }

    private external fun listNative(path: String, password: String, options: String): String
    private external fun extractNative(path: String, name: String, target: String, password: String, options: String)
    private external fun createNative(source: String, target: String, format: String, password: String, options: String)

    fun list(file: File, password: String, format: String): JSONObject {
        // A JSON array is returned by Rust. Wrap it to keep the Kotlin interface typed.
        return JSONObject().put("entries", org.json.JSONArray(listNative(
            file.absolutePath, password, ArchiveSettings.optionsFor(format)
        )))
    }

    fun extract(file: File, entry: String, target: File, password: String, format: String) {
        extractNative(file.absolutePath, entry, target.absolutePath, password,
            ArchiveSettings.optionsFor(format))
    }

    fun create(source: File, target: File, format: String, password: String, override: String? = null) {
        createNative(source.absolutePath, target.absolutePath, format, password,
            ArchiveSettings.optionsFor(format, override))
    }
}
