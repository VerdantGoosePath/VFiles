package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserCommandContextTest {
    @Test fun writableSelectionExposesOperations() {
        val commands = FileBrowserCommandContext(selectedCount = 3).availableCommands()
        assertTrue(FileBrowserSelectionCommand.CUT in commands)
        assertTrue(FileBrowserSelectionCommand.COPY in commands)
        assertTrue(FileBrowserSelectionCommand.DELETE in commands)
        assertTrue(FileBrowserSelectionCommand.ARCHIVE in commands)
        assertTrue(FileBrowserSelectionCommand.SHARE in commands)
        assertFalse(FileBrowserSelectionCommand.PICK in commands)
        assertFalse(FileBrowserSelectionCommand.EXTRACT in commands)
    }

    @Test fun readOnlySelectionProtectsMutations() {
        val commands = FileBrowserCommandContext(
            selectedCount = 2, selectedReadOnly = true, currentReadOnly = true
        ).availableCommands()
        assertFalse(FileBrowserSelectionCommand.CUT in commands)
        assertFalse(FileBrowserSelectionCommand.DELETE in commands)
        assertFalse(FileBrowserSelectionCommand.ARCHIVE in commands)
        assertTrue(FileBrowserSelectionCommand.COPY in commands)
        assertTrue(FileBrowserSelectionCommand.SHARE in commands)
    }

    @Test fun singlePickerOffersOnlyPickAndClose() {
        val commands = FileBrowserCommandContext(
            selectedCount = 1, picker = true
        ).availableCommands()
        assertEquals(
            setOf(FileBrowserSelectionCommand.CLEAR, FileBrowserSelectionCommand.PICK),
            commands
        )
    }

    @Test fun multiPickerCanSelectAll() {
        val commands = FileBrowserCommandContext(
            selectedCount = 1, picker = true, pickerCanSelectMultiple = true
        ).availableCommands()
        assertTrue(FileBrowserSelectionCommand.SELECT_ALL in commands)
        assertFalse(FileBrowserSelectionCommand.DELETE in commands)
    }

    @Test fun pasteStateRespectsDestinationEligibility() {
        val writable = FileBrowserCommandContext(pasteCount = 25).availableCommands()
        assertTrue(FileBrowserSelectionCommand.PASTE in writable)
        val readOnly = FileBrowserCommandContext(
            pasteCount = 25, currentReadOnly = true
        ).availableCommands()
        assertEquals(setOf(FileBrowserSelectionCommand.CANCEL_PASTE), readOnly)
    }

    @Test fun archiveExtractionIsContextual() {
        val commands = FileBrowserCommandContext(
            selectedCount = 1, selectedArchiveFiles = true
        ).availableCommands()
        assertTrue(FileBrowserSelectionCommand.EXTRACT in commands)
    }
}
