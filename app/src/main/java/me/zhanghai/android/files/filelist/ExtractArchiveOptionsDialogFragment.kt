package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isGone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.ExtractArchiveOptionsDialogBinding
import me.zhanghai.android.files.provider.archive.archiver.ArchiveSettings
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

/**
 * Options for extracting archive selections. The normal paste workflow remains
 * responsible for picking the destination and resolving conflicting file names.
 * Settings are persisted when confirmed so that ArchiveReader (including
 * network/root providers) sees the same reader settings when copy begins.
 */
class ExtractArchiveOptionsDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var binding: ExtractArchiveOptionsDialogBinding

    private fun choiceAdapter(array: Int): ArrayAdapter<CharSequence> =
        ArrayAdapter.createFromResource(
            requireContext(), array, android.R.layout.simple_spinner_item
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = ExtractArchiveOptionsDialogBinding.inflate(LayoutInflater.from(requireContext()))
        val names = args.files.map { it.path.fileName.toString().lowercase() }
        val showZip = names.any { name ->
            listOf(".zip", ".jar", ".apk", ".cbz").any { name.endsWith(it) }
        }
        val showRar = names.any { it.endsWith(".rar") || it.endsWith(".cbr") }
        val show7z = names.any { it.endsWith(".7z") }

        binding.zipPanel.isGone = !showZip
        binding.rarPanel.isGone = !showRar
        binding.sevenZHint.isGone = !show7z
        binding.rarVerify.isChecked = ArchiveSettings.preferences.getBoolean(
            ArchiveSettings.RAR_VERIFY, true
        )

        binding.zipEngineSpinner.adapter = choiceAdapter(R.array.archive_zip_engine_entries)
        binding.zipEngineSpinner.setSelection(if (ArchiveSettings.zipEngine() == "ripzip") 1 else 0)
        binding.zipEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.zipEngineHint.setText(
                    if (position == 1) R.string.archive_zip_ripzip_hint
                    else R.string.archive_zip_rawzip_hint
                )
            }
        }

        val limits = resources.getStringArray(R.array.archive_max_entry_values)
        binding.maxEntrySpinner.adapter = choiceAdapter(R.array.archive_max_entry_entries)
        val savedLimit = ArchiveSettings.preferences.getString(ArchiveSettings.MAX_ENTRY_MIB, "2048")
        binding.maxEntrySpinner.setSelection(limits.indexOf(savedLimit).takeIf { it >= 0 } ?: 3)
        binding.advancedPanel.isGone = true
        binding.advancedToggle.setOnClickListener {
            binding.advancedPanel.isGone = !binding.advancedPanel.isGone
            binding.advancedToggle.setText(
                if (binding.advancedPanel.isGone) R.string.archive_advanced_toggle
                else R.string.archive_advanced_open
            )
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.archive_extract_options_title)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.archive_extract_continue) { _, _ ->
                val zipEngine = if (binding.zipEngineSpinner.selectedItemPosition == 1) "ripzip"
                    else "rawzip"
                ArchiveSettings.preferences.edit()
                    .putString(ArchiveSettings.ZIP_ENGINE, zipEngine)
                    .putBoolean(ArchiveSettings.RAR_VERIFY, binding.rarVerify.isChecked)
                    .putString(ArchiveSettings.MAX_ENTRY_MIB,
                        limits[binding.maxEntrySpinner.selectedItemPosition])
                    .apply()
                (parentFragment as? Listener)?.onExtractOptionsConfirmed(args.files)
                    ?: error("The extraction dialog needs a FileList listener")
            }
            .create()
    }

    @Parcelize
    class Args(val files: FileItemSet) : ParcelableArgs

    interface Listener {
        fun onExtractOptionsConfirmed(files: FileItemSet)
    }

    companion object {
        fun show(files: FileItemSet, parent: androidx.fragment.app.Fragment) {
            ExtractArchiveOptionsDialogFragment().putArgs(Args(files)).show(parent)
        }
    }
}
