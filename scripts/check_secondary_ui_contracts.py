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
print("Secondary UI contract checks passed")
