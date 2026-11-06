package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.CheckBox
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.CreateArchiveDialogBinding
import me.zhanghai.android.files.databinding.NameDialogNameIncludeBinding
import me.zhanghai.android.files.provider.archive.archiver.ArchiveFormats
import me.zhanghai.android.files.provider.archive.archiver.ArchiveSettings
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.setTextWithSelection
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.takeIfNotEmpty
import org.json.JSONObject

/**
 * Format-specific settings are shown only when relevant. The modal starts from
 * each backend's saved defaults and sends overrides with the background job.
 */
class CreateArchiveDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()
    override val binding: Binding get() = super.binding as Binding
    override val listener: Listener get() = super.listener as Listener

    @StringRes override val titleRes: Int = R.string.file_create_archive_title

    override fun onInflateBinding(inflater: LayoutInflater): NameDialogFragment.Binding =
        Binding.inflate(inflater)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        if (savedInstanceState == null) {
            val files = args.files
            val name = if (files.size == 1) files.single().path.fileName.toString() else {
                files.mapTo(mutableSetOf()) { it.path.parent }.singleOrNull()
                    ?.takeIf { it.nameCount > 0 }?.fileName?.toString()
            }
            name?.let { binding.nameEdit.setTextWithSelection(it) }
        }
        binding.zipEngineSpinner.adapter = spinnerAdapter(R.array.archive_zip_engine_entries)
        binding.zipEngineSpinner.setSelection(if (ArchiveSettings.zipEngine() == "ripzip") 1 else 0)
        binding.zipEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshMethods()
                refreshVisibility()
            }
        }
        binding.typeGroup.setOnCheckedChangeListener { _, _ ->
            refreshLevel()
            refreshVisibility()
        }
        binding.advancedToggle.setOnClickListener {
            binding.advancedPanel.isGone = !binding.advancedPanel.isGone
            binding.advancedToggle.setText(if (binding.advancedPanel.isGone)
                R.string.archive_advanced_toggle else R.string.archive_advanced_open)
        }
        binding.compressionLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.compressionLevelLabel.text = getString(R.string.archive_level_label) + ": $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.sevenZSolid.isChecked =
            ArchiveSettings.preferences.getBoolean(ArchiveSettings.SEVENZ_SOLID, false)
        binding.sevenZEncryptHeaders.isChecked =
            ArchiveSettings.preferences.getBoolean(ArchiveSettings.SEVENZ_ENCRYPT_HEADERS, true)
        refreshMethods()
        refreshLevel()
        refreshVisibility()
        return dialog
    }

    private fun spinnerAdapter(entries: Int): ArrayAdapter<CharSequence> =
        ArrayAdapter.createFromResource(requireContext(), entries,
            android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private val format: Int get() = when (binding.typeGroup.checkedRadioButtonId) {
        R.id.zipRadio -> ArchiveFormats.ZIP
        R.id.tarXzRadio -> ArchiveFormats.TAR_XZ
        R.id.sevenZRadio -> ArchiveFormats.SEVEN_Z
        else -> ArchiveFormats.ZIP
    }

    private val zipEngine: String get() =
        if (binding.zipEngineSpinner.selectedItemPosition == 1) "ripzip" else "rawzip"

    private val formatName: String get() = ArchiveFormats.name(format)

    override val name: String get() = "${super.name}.${formatName}"

    private fun refreshLevel() {
        binding.compressionLevel.progress = ArchiveSettings.level(formatName, zipEngine)
        binding.compressionLevelLabel.text = getString(R.string.archive_level_label) +
            ": ${binding.compressionLevel.progress}"
    }

    private fun refreshMethods() {
        val ripzip = zipEngine == "ripzip"
        val entries = if (ripzip) R.array.archive_ripzip_method_entries
            else R.array.archive_rawzip_method_entries
        binding.zipMethodSpinner.adapter = spinnerAdapter(entries)
        val prefKey = if (ripzip) ArchiveSettings.RIPZIP_METHOD else ArchiveSettings.RAWZIP_METHOD
        val selected = ArchiveSettings.preferences.getString(prefKey, "deflate")
        binding.zipMethodSpinner.setSelection(if (selected == "store" || selected == "zstd") 1 else 0)
        if (format == ArchiveFormats.ZIP) refreshLevel()
    }

    private fun refreshVisibility() {
        val zip = format == ArchiveFormats.ZIP
        val sevenZ = format == ArchiveFormats.SEVEN_Z
        binding.zipEnginePanel.isGone = !zip
        binding.zipMethodLabel.isGone = !zip
        binding.zipMethodSpinner.isGone = !zip
        binding.zipPasswordNote.isGone = !zip
        binding.passwordLayout.isGone = !sevenZ
        binding.sevenZSolid.isGone = !sevenZ
        binding.sevenZEncryptHeaders.isGone = !sevenZ
        binding.engineHint.setText(
            when {
                sevenZ -> R.string.archive_sevenz_engine_hint
                !zip -> R.string.archive_tar_engine_hint
                zipEngine == "ripzip" -> R.string.archive_zip_ripzip_hint
                else -> R.string.archive_zip_rawzip_hint
            }
        )
    }

    override fun onOk(name: String) {
        val method = if (binding.zipMethodSpinner.selectedItemPosition == 1) {
            if (zipEngine == "ripzip") "zstd" else "store"
        } else "deflate"
        // Changes in the modal become the next operation's defaults, and the
        // settings screen switches immediately to the selected ZIP engine.
        val levelKey = when (format) {
            ArchiveFormats.ZIP -> if (zipEngine == "ripzip")
                ArchiveSettings.RIPZIP_LEVEL else ArchiveSettings.RAWZIP_LEVEL
            ArchiveFormats.SEVEN_Z -> ArchiveSettings.SEVENZ_LEVEL
            else -> ArchiveSettings.TAR_LEVEL
        }
        ArchiveSettings.preferences.edit()
            .putString(ArchiveSettings.ZIP_ENGINE, zipEngine)
            .putInt(levelKey, binding.compressionLevel.progress)
            .putString(if (zipEngine == "ripzip") ArchiveSettings.RIPZIP_METHOD
                else ArchiveSettings.RAWZIP_METHOD, method)
            .putBoolean(ArchiveSettings.SEVENZ_SOLID, binding.sevenZSolid.isChecked)
            .putBoolean(ArchiveSettings.SEVENZ_ENCRYPT_HEADERS,
                binding.sevenZEncryptHeaders.isChecked)
            .apply()
        val override = JSONObject()
            .put("zip_engine", zipEngine)
            .put("zip_method", method)
            .put("level", binding.compressionLevel.progress)
            .put("sevenz_solid", binding.sevenZSolid.isChecked)
            .put("sevenz_encrypt_headers", binding.sevenZEncryptHeaders.isChecked)
            .toString()
        val password = if (format == ArchiveFormats.SEVEN_Z)
            binding.passwordEdit.text?.toString()?.takeIfNotEmpty() else null
        listener.archive(args.files, name, format, 0, password, override)
    }

    companion object {
        fun show(files: FileItemSet, fragment: Fragment) {
            CreateArchiveDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize class Args(val files: FileItemSet) : ParcelableArgs

    protected class Binding private constructor(
        root: View,
        nameLayout: TextInputLayout,
        nameEdit: EditText,
        val typeGroup: RadioGroup,
        val passwordLayout: TextInputLayout,
        val passwordEdit: TextInputEditText,
        val zipEnginePanel: LinearLayout,
        val zipEngineSpinner: Spinner,
        val zipPasswordNote: TextView,
        val engineHint: TextView,
        val compressionLevel: SeekBar,
        val compressionLevelLabel: TextView,
        val advancedToggle: TextView,
        val advancedPanel: LinearLayout,
        val zipMethodSpinner: Spinner,
        val zipMethodLabel: TextView,
        val sevenZSolid: CheckBox,
        val sevenZEncryptHeaders: CheckBox
    ) : NameDialogFragment.Binding(root, nameLayout, nameEdit) {
        companion object {
            fun inflate(inflater: LayoutInflater): Binding {
                val b = CreateArchiveDialogBinding.inflate(inflater)
                val n = NameDialogNameIncludeBinding.bind(b.root)
                return Binding(b.root, n.nameLayout, n.nameEdit, b.typeGroup,
                    b.passwordLayout, b.passwordEdit, b.zipEnginePanel, b.zipEngineSpinner,
                    b.zipPasswordNote, b.engineHint, b.compressionLevel, b.compressionLevelLabel,
                    b.advancedToggle, b.advancedPanel, b.zipMethodSpinner, b.zipMethodLabel,
                    b.sevenZSolid, b.sevenZEncryptHeaders)
            }
        }
    }

    interface Listener : FileNameDialogFragment.Listener {
        fun archive(files: FileItemSet, name: String, format: Int, filter: Int,
            password: String?, optionsOverride: String?)
    }
}
