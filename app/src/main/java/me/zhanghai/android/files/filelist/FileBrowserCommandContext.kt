package me.zhanghai.android.files.filelist

/** Pure browser command eligibility, separate from provider and UI concerns. */
data class FileBrowserCommandContext(
    val selectedCount: Int = 0,
    val selectedReadOnly: Boolean = false,
    val selectedArchiveFiles: Boolean = false,
    val currentReadOnly: Boolean = false,
    val picker: Boolean = false,
    val pickerCanSelectMultiple: Boolean = false,
    val pasteCount: Int = 0
) {
    fun availableCommands(): Set<FileBrowserSelectionCommand> {
        if (selectedCount == 0) {
            return if (pasteCount > 0 && !picker) {
                buildSet {
                    add(FileBrowserSelectionCommand.CANCEL_PASTE)
                    if (!currentReadOnly) add(FileBrowserSelectionCommand.PASTE)
                }
            } else emptySet()
        }
        return buildSet {
            add(FileBrowserSelectionCommand.CLEAR)
            if (!picker || pickerCanSelectMultiple) add(FileBrowserSelectionCommand.SELECT_ALL)
            if (picker) {
                add(FileBrowserSelectionCommand.PICK)
                return@buildSet
            }
            add(FileBrowserSelectionCommand.COPY)
            add(FileBrowserSelectionCommand.SHARE)
            if (!selectedReadOnly) {
                add(FileBrowserSelectionCommand.CUT)
                add(FileBrowserSelectionCommand.DELETE)
            }
            if (selectedArchiveFiles) add(FileBrowserSelectionCommand.EXTRACT)
            if (!currentReadOnly) add(FileBrowserSelectionCommand.ARCHIVE)
        }
    }
}

fun FileBrowserChromeState.commandContext() = FileBrowserCommandContext(
    selectedCount = selectedCount,
    selectedReadOnly = selectedReadOnly,
    selectedArchiveFiles = selectedArchiveFiles,
    currentReadOnly = currentReadOnly,
    picker = picker,
    pickerCanSelectMultiple = pickerCanSelectMultiple,
    pasteCount = pasteCount
)
