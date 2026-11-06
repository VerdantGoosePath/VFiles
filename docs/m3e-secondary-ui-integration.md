# Phase E — secondary UI merge notes

Parent m3e/integration: fa8db3a069f34caa946c16828f2326d406af5634.
Feature branch: m3e/secondary-ui. Never merge or modify main/integration here.

Stable calling contracts: FilePropertiesDialogFragment.show(FileItem, Fragment),
OpenFileActivity.createIntent(Path, MimeType), EditFileActivity.Args(Path, MimeType),
OpenFileAsDialogFragment.Args(Path), FileJobService.open(Path, MimeType, Boolean, Context),
and Edit*ServerFragment.Args retain their signatures. Storage item serialization,
Storages.move/addOrReplace/remove, SAF grants, SFTP private-key picker and FTP service remain.

Browser merge collision: app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt.
Preserve removal of maybeAddImageViewerActivityExtras and ImageViewerActivity references.
Open via ACTION_VIEW and FileProvider content URI with read/write flags where applicable,
offer Open with external chooser and ACTION_EDIT trampoline, honor user-selected external
default app after resolving the package, stage archive files through FileJobService.open.
Do not restore internal ImageViewerActivity/TextEditorActivity manifest filters.

Other collision candidates: AndroidManifest.xml, app/build.gradle,
app/src/main/res/values*/strings.xml, settings BookmarkDirectoryListAdapter,
about/AboutFragment.kt. All viewer/editor strings and layout/menu resources were removed.
Keep Coil, Coil Video, EXIF and the properties metadata tabs.

Storage addition chooser and About use VFilesTheme + segmented Compose components.
Connection forms deliberately retain proven View/IME/editing logic but share
sanitized error dialog and explicit destructive confirmation; connection state
and credential parsing still belong to original ViewModels.

Validation: .github/workflows/m3e-secondary-ui.yml builds APK, AndroidTest APK, JVM
unit-test tasks, optional systemPicker; script check_secondary_ui_contracts.py checks
viewer removal and metadata/URI protocols. Device tests still needed for SAF
grant persistence, ACTION_VIEW no-handler, Android preferred apps, archive staging,
remote connections (all auth modes), LAN discovery, filejob conflict/cancel,
metadata/permissions details, D-pad and TalkBack order, IME and font scale 2.0.
