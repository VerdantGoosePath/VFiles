# Phase E — secondary UI implementation and merge contract

## Git isolation

Base: `origin/m3e/integration` at `fa8db3a069f34caa946c16828f2326d406af5634`.
Work branch: `m3e/secondary-ui`. Never merge, reset or push to `m3e/integration` or `main`.
Each new GitHub Actions run checks the isolated branch: `.github/workflows/m3e-secondary-ui.yml`.

## Implemented secondary surfaces

- Native M3E Compose (each under shared `VFilesTheme`): storage listing/order/action;
  storage-type chooser; FTP server controls and live service status; external default-app
  selection; standard-directory toggles; permission onboarding; About. These use
  `MaterialExpressiveTheme`, segmented list shapes and appropriate semantic color roles.
- Storage reorder is a two-step accessible up/down command in the Compose list, using the
  original `Storages.move` persistence path. The legacy touch drag adapter was removed.
  This is a **deliberate UX change**; test with D-pad/TalkBack before integration.
- Remote FTP/SFTP/SMB/WebDAV forms retain the complex proven View/IME/network credential
  inputs and original ViewModels. All validate port 1..65535, keep progress overlaid above
  a temporarily interaction-blocked form, cancel the in-flight coroutine safely, redact
  connection exceptions, and require confirmation before removing a saved server.
  File pickers and private-key logic remain the original ActivityResult contracts.
- LAN SMB discovery retains network logic and manual add, with a snackbar offering retry.
  SAF trees, Android/data shortcuts and bookmarks require explicit removal confirmation;
  volume eject continues to launch system settings rather than trying privileged unmount.
- File properties retain *every* available tab: Basic, permission/owner/group/mode/SELinux,
  EXIF image, audio/video metadata, APK/permissions and checksum/compare. The existing
  child fragments and metadata LiveData remain; error UI now offers retry without
  leaking exception strings, and one-tab properties do not show useless tab chrome.
- File-job conflict/error, archive password, create/rename/delete/replace and color
  selection retain View interop where migrating IME or RemoteCallback parcelers carries
  unacceptable risk. Large-font conflict layout, explicit Delete/Replace actions,
  non-spammable remount state, accessible color swatches and credential autofill suppression
  were improved while preserving callbacks and settings persistence.

## Stable public contracts (browser/Settings integration)

- `FilePropertiesDialogFragment.show(FileItem, Fragment)`, all file-properties
  fragment arguments/tabs and the existing checksum/metadata sources remain.
- `OpenFileActivity.createIntent(Path, MimeType)`, `EditFileActivity.Args(Path, MimeType)`,
  `OpenFileAsDialogFragment.Args(Path)` and `FileJobService.open(Path, MimeType, Boolean, Context)`
  remain. Normal and archive-backed files use external `ACTION_VIEW`, external
  `ACTION_EDIT` where applicable, chooser/Open-as, and compatible content: URI grants.
  External defaults are checked with `resolveActivity` before selecting the package.
  `filelist/ExternalFileOpening.kt` is a shared helper for shortcuts and staged archives.
- The `viewer.image.ImageViewerActivity` and `viewer.text.TextEditorActivity` exported
  filters, implementations, viewer-specific extra paths, layouts/menus and strings are gone.
  **Never restore these routes** during the Browser merge. `viewer.saveas.SaveAsActivity`
  intentionally remains exported: Save As is a file-management operation, not a viewer.
- `StorageListActivity`, `AddStorageDialogActivity`, `Edit*ServerActivity`,
  `Edit*ServerFragment.Args`, `Storages.move/addOrReplace/remove`, serialized IDs and
  persisted SAF URI grants are unchanged. Settings agent owns entrypoint rows only.
- `FtpServerService`, `FtpServerTileService`, runtime network address receiver,
  SFTP key picker, provider FileProvider/DocumentProvider, and thumbnail/EXIF/Coil
  dependencies are retained.

## Expected textual collisions when merging separate agents

High: `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt`,
`app/src/main/AndroidManifest.xml`, `app/build.gradle`,
`app/src/main/res/values*/strings.xml`. Preserve external default-app/open-with logic
and deletions of the internal viewers. Browser may have reworked `FileListFragment`,
which requires a manual merge. Shared names `VFilesTheme`, `VFilesPreferenceGroup`,
and `VFilesScreenScaffold` come from Phase B and were consumed, not altered.
Medium: `filejob/FileJobs.kt` (archive open action), `filelist/OpenFileActivity.kt`,
`storage/*`, `settings/*` list fragments, `about/AboutFragment.kt`,
`onboarding/OnboardingActivity.kt`, `navigation/EditBookmarkDirectoryDialogFragment.kt`.
Do not reintroduce deleted XML `ftp_server.xml`, `default_apps_fragment.xml`,
`storage_list_fragment.xml`, `standard_directory_list_fragment.xml` or
`onboarding_activity.xml` when selecting the Compose implementations.

## Validation actually performed

CI invokes `python3 scripts/check_phase_a_contracts.py`,
`python3 scripts/check_secondary_ui_contracts.py`, and
`./gradlew --no-daemon --warning-mode all :app:assembleDebug
:app:assembleDebugAndroidTest :app:testDebugUnitTest`, plus
`./gradlew --no-daemon --warning-mode all -PsystemPicker :app:assembleSystemPickerDebug`.
`RemoteConnectionUiTest` has JVM regression cases for credential/URI redaction.
A CI passing state confirms compilation and unit tests, *not* Android device interaction.

## Manual device acceptance gate (not yet run)

1. Under Android 17, 360/393/600/840/1200dp, dark/light, 1.0/1.3/2.0 font,
   D-pad and TalkBack: add each storage type, reorder, edit, remove (cancel then confirm).
   Restart; verify IDs/order, private keys/passwords and SAF persisted grants unchanged.
2. Test FTP, SFTP password and key, SMB guest/domain, WebDAV password and token:
   invalid host, port 0/65536/non-number, wrong credentials, valid connection, abort
   in-progress test. Confirm canceled tests never save; inspect logs for secrets.
3. With/without default external app: open local/remote/archive files, chooser/Open-as,
   optional ACTION_EDIT, no-handler case, outgoing read/write grants, shortcut opening,
   SAF picker result/cancel. Check APK installation & archive navigation.
4. Exercise file-job conflicts: replace/merge/rename/skip/cancel/apply all, with the
   original RemoteCallback result payload; retry/skip/remount error actions; archive
   password success/cancel. Check progress notifications and save-as.
5. Inspect basic/checksum comparison, EXIF, audio/video, APK permissions,
   Unix mode/owner/group/SELinux tabs; change attributes and refresh. Confirm thumbnails.
6. Check onboarding required/optional grants, FTP server service/tile/URL update, LAN
   discovery failure/manual entry, bookmark/volume editing, theme/color picker, About.

Known exception: complex View-era filejob/metadata/remote dialogs are not wholly
reimplemented as Compose; this is intentional until their lifecycle/IME/parcelable
contracts can be verified on hardware. Device acceptance is required before declaring
all of Phase E production-ready.
