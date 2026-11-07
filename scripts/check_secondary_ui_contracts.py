#!/usr/bin/env python3
"""Static Phase E contract checks. Run from repository root."""
from pathlib import Path

root = Path("app/src/main")
src = root / "java/me/zhanghai/android/files"
manifest = (root / "AndroidManifest.xml").read_text()
files = (src / "filelist/FileListFragment.kt").read_text()
intents = (src / "util/IntentExtensions.kt").read_text()
gradle = Path("app/build.gradle").read_text()
for removed in ("ImageViewerActivity", "TextEditorActivity", "maybeAddImageViewerActivityExtras"):
    assert removed not in files
    assert removed not in manifest
for pkg in ("viewer/image", "viewer/text"):
    assert not (src / pkg).exists()
for retained in ("OpenFileActivity", "EditFileActivity", "OpenFileAsDialogActivity",
                 "viewer.saveas.SaveAsActivity"):
    assert retained in manifest, retained
assert "FileJobService.open" in files
assert "applyDefaultAppPackage" in files
assert ".withChooser(" in files
assert "FLAG_GRANT_READ_URI_PERMISSION" in intents
assert "FLAG_GRANT_WRITE_URI_PERMISSION" in intents
for lib in ("io.coil-kt:coil", "io.coil-kt:coil-video",
            "androidx.exifinterface:exifinterface"):
    assert lib in gradle, lib
properties = (src / "fileproperties/FilePropertiesDialogFragment.kt").read_text()
for tab in ("Basic", "Checksum", "Apk", "Image", "Audio", "Video", "Permission"):
    assert "FileProperties" + tab + "TabFragment" in properties, tab
assert "fun show(file: FileItem, fragment: Fragment)" in properties
for protocol in ("Ftp", "Sftp", "Smb", "WebDav"):
    code = (src / ("storage/Edit" + protocol + "ServerFragment.kt")).read_text()
    for contract in ("port !in 1..65535", "confirmRemoveStorage",
                     "showRemoteConnectionError", "Storages.addOrReplace"):
        assert contract in code, protocol + ": " + contract
# Phase E post-migration screens keep one expressive root and original data writes.
# Different parallel agents retained separate but valid Compose scaffolds.
# Check their actual composition roots rather than requiring one string in every wrapper.
for file in ("about/AboutFragment.kt", "storage/StorageListFragment.kt"):
    source = (src / file).read_text()
    assert "VFilesTheme" in source and "VFilesScreenScaffold" in source, file
ftp_fragment = (src / "ftpserver/FtpServerFragment.kt").read_text()
ftp_screen = (src / "ftpserver/ExpressiveFtpServerScreen.kt").read_text()
assert "VFilesTheme" in ftp_fragment
assert "ExpressiveFtpServerScreen" in ftp_fragment
assert "Scaffold(" in ftp_screen
for file in ("settings/DefaultAppsFragment.kt",
             "settings/StandardDirectoryListFragment.kt"):
    source = (src / file).read_text()
    assert "VFilesTheme" in source and "Scaffold(" in source, file
assert "VFilesTheme" in (src / "onboarding/OnboardingActivity.kt").read_text()
assert "Storages.move" in (src / "storage/StorageListFragment.kt").read_text()
assert "Storages.addOrReplace" in (src / "storage/EditFtpServerFragment.kt").read_text()
for protocol in ("Ftp", "Sftp", "Smb", "WebDav"):
    vm = (src / f"storage/Edit{protocol}ServerViewModel.kt").read_text()
    assert "fun cancelConnecting()" in vm, protocol
    assert "ensureActive()" in vm, protocol
    ui = (src / f"storage/Edit{protocol}ServerFragment.kt").read_text()
    assert "binding.connectingInputBlocker.isVisible" in ui, protocol
    layout = (root / f"res/layout/edit_{protocol.lower()}_server_fragment.xml").read_text()
    assert layout.index("connectingInputBlocker") > layout.index("</androidx.core.widget.NestedScrollView>"), protocol
    assert layout.index('android:id="@+id/progress"') > layout.index("connectingInputBlocker"), protocol

# Internal viewer extras are intentionally absent from external intents.
jobs = (src / "filejob/FileJobs.kt").read_text()
shortcut = (src / "filelist/OpenFileActivity.kt").read_text()
assert "applyExternalDefaultOpenApp(mimeType, service)" in jobs
assert ".apply { extraPath = file }" not in jobs
assert "applyExternalDefaultOpenApp(mimeType, this)" in shortcut
assert "viewer.saveas.SaveAsActivity" in manifest  # Save As remains file management.
print("Secondary UI contract checks passed")
