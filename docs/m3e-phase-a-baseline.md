# Phase A — Android 17 / Material 3 Expressive migration baseline

Recorded 2026-11-06. This is a *preflight snapshot*, not a screen/design migration and not a substitute for each later phase's own prompt. Source of truth: fork `VerdantGoosePath/VFiles`, `main` at `f0c0f4d263d2b2422d1e9651a8a0b2884172f3b3`. `m3e/integration` was created **directly from that SHA**. No code, theme, SDK target, runtime permissions or viewer routes are changed in Phase A.

## Build environment and repeatable commands

Single Gradle module `:app`; Gradle wrapper **9.3.1**, Android Gradle Plugin **9.1.0**, Kotlin Gradle Plugin **2.3.20**, Java source/target **11** (JDK **21** used in CI). `compileSdk 36`, `targetSdk 34`, `minSdk 23`, build tools **37.0.0**, NDK **28.1.13356709**, CMake **3.22.1** in CI. App ID `fr.vroot.vfiles.fork`, version `1.8.4 (44)`. AndroidX Views, MDC Android `com.google.android.material:material:1.13.0`, AppCompat, RecyclerView, LiveData, XML preferences; no Compose BOM/material3 Compose dependency, Compose plugin or Compose-based app root. The Material 3 switch selects MDC/XML `Theme.MaterialFiles.Material3`; it is **not Compose Material 3 Expressive**.

For a fresh checkout of the exact baseline:

```sh
git clone https://github.com/VerdantGoosePath/VFiles.git
cd VFiles
git fetch origin main
git checkout --detach f0c0f4d263d2b2422d1e9651a8a0b2884172f3b3
java -version
./gradlew --version
python3 scripts/check_phase_a_contracts.py  # available on integration branch, not original main
./gradlew --no-daemon --warning-mode all :app:assembleDebug :app:testDebugUnitTest
./gradlew --no-daemon --warning-mode all -PsystemPicker :app:assembleSystemPickerDebug :app:testSystemPickerDebugUnitTest
```

Install Android SDK platform `android-36`, build tools `37.0.0`, NDK `28.1.13356709` and CMake `3.22.1` before Gradle, or use the branch-specific GitHub Actions workflow `m3e-preflight.yml`. No release signing is required for debug builds. Release signing uses optional `keystore.properties` or environment variables; the existing main workflow explicitly requires four release signing secrets and should not be used as a debug test harness. When the `-PsystemPicker` property is present, only the `systemPicker` flavor is registered: use `assembleSystemPickerDebug`, not `assembleDebug` for that variant.

### Observed baseline results; separate observation from expectation

- Local environment observed: Debian container, Git **2.47.3**, OpenJDK **21.0.12.1**, local standalone `kotlinc-jvm 1.9.0` (not the project's Gradle compiler); Python **3.13.5**. Neither `ANDROID_HOME` nor `ANDROID_SDK_ROOT` is set; `adb`, an installed Android SDK/NDK, and a local `gradle` command are unavailable.
- Attempted `git clone --depth 1 --branch main https://github.com/VerdantGoosePath/VFiles.git source` from this container: **failed** with `Could not resolve host: github.com`. Consequently **no fresh Gradle assemble/test command or device test could be executed locally**. The Gradle commands above are reproducible instructions, **not local test results**. This is an environment blocker, not evidence of an app regression.
- Existing GitHub Actions run [41374136461](https://github.com/VerdantGoosePath/VFiles/actions/runs/41374136461) on **2026-11-02**, exact `main` SHA above: `./gradlew --no-daemon assembleRelease` **succeeded** after provisioning SDK/NDK/CMake and release-signing secrets. This is **historical CI evidence only**; it does **not** establish debug, unit-test or system-picker success. Previous run [41373976784](https://github.com/VerdantGoosePath/VFiles/actions/runs/41373976784) failed on the preceding commit, before the SDK setup fix.
- Warnings in that CI log: a Kotlin override-deprecation diagnostic at `storage/AddDocumentTreeFragment.kt:24`; Gradle deprecated features incompatible with Gradle 10 (use `--warning-mode all` for diagnostics); `actions/setup-java@v4` and Node/punycode deprecation notices.
- No `app/src/test` or `app/src/androidTest` source exists at the baseline snapshot (0 checked-in JVM/instrumented tests). `:app:testDebugUnitTest` can therefore be a no-test Gradle task, not proof of behavioral coverage. The newly added pure-stdlib checker asserts only static contracts. CI smoke test results must be read from their actual run, not inferred from the workflow definition.

## Actual source boundaries

- **Frontend/shell**: `app/AppActivity.kt`, `filelist/{FileListActivity,FileListFragment,FileListViewModel,FileListAdapter}.kt`, `navigation/{NavigationFragment,NavigationListAdapter,NavigationItems,NavigationRoot}.kt` and `ui/*`. Main shell is activity + fragment, RecyclerView list/grid, breadcrumb layout, search view, drawer, toolbar/action modes and speed dial. `FileListFragment` is ~2,183 lines and handles picker return values, permissions, menu actions, selection/paste and custom insets; `FileListViewModel` owns trail/path, search, sort, selection and paste LiveData; `FileListAdapter` binds file views and drives keyboard/menu actions.
- **More frontend routes/forms**: `storage/*` (device/SAF shortcuts; FTP, SFTP, SMB, WebDAV server editors; LAN SMB search; storage lists), `settings/*`, `theme/custom/*` and `theme/night/*`, `onboarding/*`, `fileproperties/*`, `about/*`, `ftpserver/*` UI. `settings/SettingLiveData.kt` persists through `SharedPreferences`, **not DataStore**. Preserved keys include hidden files, view type, sorting, per-path view/sort, bookmarks, roots, default apps, theme color, M3 toggle, night/black mode and edge-to-edge.
- **Jobs/domain boundary**: `filejob/{FileJobService,FileJobs,FileJobProgressLiveData,FileJobReceiver}.kt`, error/conflict dialogs, progress notifications and cancel actions. `FileJobs.kt` implements archive, copy, move, delete, rename, open, create, save, APK install and POSIX/SELinux operations. Treat executor/cancellation/error/conflict behavior as backend contracts even if progress UI is replaced.
- **Provider/backend boundary — must not be dropped**: `provider/{linux,root,remote,archive,ftp,sftp,smb,webdav,document,content,common}/*`, `file/{FileProvider,FileSystemDocumentsProvider,ExternalStoragePickerUri}.kt`. This includes local filesystem, Shizuku/root, archive reader/writer, libarchive, NIO path abstraction, remote FTP/SFTP/SMB/WebDAV, URI grants and Android DocumentsProvider. Keep provider code independent of Compose UI ownership.
- **Resource boundaries**: XML layouts under `app/src/main/res/layout` (notably `file_list_fragment*.xml`, `file_item_{list,grid}.xml`, `navigation_fragment.xml`, `settings_fragment.xml`, `onboarding_activity.xml`, network connection forms, and current text/image viewer layouts); menu XML under `res/menu`; prefs `res/xml/settings.xml`; base, night, M3, dynamic/preset/black themes under `res/values*`; screenshots would need real devices/emulators, not generated images.
- **Native/build**: `app/CMakeLists.txt`, `app/src/main/jni`, AIDL `provider/remote/IRemoteFileService.aidl`, bundled `local-maven` dav4jvm artefact, root `signing.gradle`, Gradle wrapper, and `.github/workflows/android.yml`.

### Manifest inventory (31 activities, 4 services, 3 providers, 2 receivers)

- **Main/pickers**: `FileListActivity` (LAUNCHER, LEANBACK_LAUNCHER, VIEW directory/archive, GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE and VIEW_DOWNLOADS); `OpenFileActivity`, `EditFileActivity`, `OpenFileAsDialogActivity`.
- **Storage/navigation**: `AddStorageDialogActivity`, `EditDeviceStorageDialogActivity`, `AddExternalStorageShortcutActivity`, `EditExternalStorageShortcutDialogActivity`, `AddDocumentTreeActivity`, `EditDocumentTreeDialogActivity`, `EditFtpServerActivity`, `EditSftpServerActivity`, `AddLanSmbServerActivity`, `EditSmbServerActivity`, `EditWebDavServerActivity`, `EditBookmarkDirectoryDialogActivity`, `EditStorageVolumeDialogActivity`.
- **Settings/misc**: `OnboardingActivity`, `FtpServerActivity`, `SettingsActivity`, `StorageListActivity`, `StandardDirectoryListActivity`, `BookmarkDirectoryListActivity`, `DefaultAppsActivity`, `AboutActivity`.
- **Jobs/viewers**: `ArchivePasswordDialogActivity`, `FileJobErrorDialogActivity`, `FileJobConflictDialogActivity`, `SaveAsActivity` (external content ACTION_VIEW/SEND), `TextEditorActivity` (externally VIEW-able), `ImageViewerActivity` (externally VIEW-able, immersive). **Do not remove viewers, their intent filters, or SaveAs in Phase A.** In Phase E, preserve external app opening, MIME/URI grants, sharing, thumbnails and metadata while removing only the specified internal viewing/editing UIs.
- **Services**: `FileJobService` and `FtpServerService` (both `foregroundServiceType="dataSync"`), `FtpServerTileService` (Quick Settings tile), `AppLocalesMetadataHolderService`.
- **Providers**: `AppProvider` (private), `FileProvider` (private, URI grants), `FileSystemDocumentsProvider` (exported, URI grants, protected by `MANAGE_DOCUMENTS` and ACTION_DOCUMENTS_PROVIDER). **Receivers**: `FileJobReceiver` (cancel) and `FtpServerReceiver`.
- Manifest permissions include `MANAGE_EXTERNAL_STORAGE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `INTERNET`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `WAKE_LOCK`, storage write up to API 32 and network/Wi-Fi state. App declares touchscreen, Wi-Fi and leanback optional; RTL support and resizability are enabled, `enableOnBackInvokedCallback=true`.
- **System picker variant**: `app/src/systemPicker/AndroidManifest.xml` adds *only* `MANAGE_DOCUMENTS` as a uses-permission when `-PsystemPicker` registers the flavor. `ExternalStoragePickerUri.kt` checks whether it was actually granted before returning `com.android.externalstorage.documents` URIs for applicable local existing files/folders. Platform signature is essential; vanilla debug APK falls back to its own provider. `CREATE_DOCUMENT` keeps its own URI. See `docs/systempicker-integration.md`; test strict third-party persisted grants only on a platform-signed ROM. Do not equate a successful debug build with a functional platform picker.

## Reproducible behavioral matrix (all cases **UNRUN** on this agent)

Run against the original `main` baseline before migration and repeat after each relevant phase. Log actual pass/fail, device API, screen size, logs, screenshot file and backend fixture; **do not mark any case as passing from code inspection**.

| ID | Workflow / observation to preserve | Fixture, special risk |
| --- | --- | --- |
| B01 | Cold launch, permission/onboarding required vs optional cards, resume after settings grant | Fresh install and existing install; deny/allow all-files/notifications/install |
| B02 | Local storage root, internal/SD/USB and SAF tree navigation; breadcrumbs and Back/Up | Nested folders, removable volume, provider read-only state |
| B03 | Show/hide dotfiles, sort name/type/size/date ± direction/directories first; list/grid and per-path setting | Mixed directory of fixed names/sizes and persistent restart |
| B04 | Search, clear, reload, cancel; empty/error state; no stale results after path switch | Large local folder and slow remote server |
| B05 | Single/multi/range/select-all keyboard/long-press selection; selected toolbar and Back unwind | Mixed files/directories and read-only items |
| B06 | Cut/copy/paste/move/cancel clipboard; conflict replace/skip/all; same-path and cross-provider | Local↔remote, 0-byte/large file, overwrite and permission error |
| B07 | Delete, rename, create file/dir, undo or explicit confirmation where present; read-only errors | Make exact success/failure observable, never silently drop files |
| B08 | Create archives with format/password; browse nested archives; extract to destination | ZIP/7z/TAR or supported formats, encrypted, corrupt archive |
| B09 | Job progress bytes/count, notification, conflict/error dialogs, cancel, background/rotation | Slow large transfer, intermittent network, denied notification |
| B10 | FTP/SFTP/SMB/WebDAV add/edit/login/reconnect; cert/host-key and credential behavior | Controlled LAN + WAN fixtures; auth failure, timeout, read-only share |
| B11 | LAN SMB discovery and in-app FTP server, Quick Settings tile | API 37 local-network permission and dataSync timeout high risk |
| B12 | Storage volumes, bookmarks/custom roots, default directory, path-specific state | Unmount/reinsert, invalid saved path, process death |
| B13 | GET_CONTENT/OPEN_DOCUMENT/CREATE_DOCUMENT/TREE picker, URI MIME, flags, ClipData and persisted grants | External test caller; local, SAF and remote paths; get-content read-only |
| B14 | SystemPicker variant strict external-storage-provider URIs with real platform signing | ROM-signing fixture; no claim for ordinary debug |
| B15 | External open/default app/chooser, edit with external app, file share, APK install | Existing viewer routes still present until Phase E |
| B16 | Content provider and DocumentsProvider query/open/create/delete/rename; range and writable descriptors | External consuming app, content:// grants, read-only paths |
| B17 | Theme preset/dynamic/M3 toggle/system day/night/black; settings persistence/recreation | Android 12+ wallpaper variants, API 23 fallback |
| B18 | Portrait/landscape/tablet/fold/freeform resize; drawer, bottom bar, insets, cutout, IME | 360/393/600/840/1200dp; gesture and 3-button nav |
| B19 | Hardware keyboard, D-pad/TV focus, select/open/navigation, escape/back, shortcuts | Leanback emulator/TV and keyboard; external mouse |
| B20 | RTL, 1.0/1.3/2.0 font scale, TalkBack, contrast, disabled focus and reduced motion | Long translated labels, dynamic light/dark, 48dp targets |

**Screenshot/behavior collection not performed**: no reachable emulator/device/ADB. Required baseline capture matrix remains 360/393/600/840dp at font 1.0/1.3/2.0, day/night, RTL/LTR, gesture/three-button, keyboard/TV, plus 320/1200dp and large-screen resize where possible. No screenshots, interactive frame metrics, accessibility results or regression-free claims exist for this phase.

## Android 17 / API 37 risk register — research, not implementation

1. **P0 — LAN permission (new for target 37).** Android 17 requires `ACCESS_LOCAL_NETWORK` runtime permission for direct local-network communication; evaluate SMB discovery, LAN FTP/SFTP/WebDAV connections, user-hosted FTP server, multicast/broadcast, and network permission prompts. Do not add/alter permissions yet. [Android 17 targeting changes](https://developer.android.com/about/versions/17/behavior-changes-17).
2. **P0 — raw system insets.** `ui/PersistentBarLayout.kt` explicitly documents coupling to `ScrollingViewOnApplyWindowInsetsListener.kt` and uses `WindowInsets.systemWindowInset*`, `replaceSystemWindowInsets` and `consumeSystemWindowInsets` through drag, measurement, bar offsets and scrolling/fast scroller. `FileListFragment` manually applies bottom padding and insets, drawer appearance, app-bar/action-mode/IME behavior. Edge-to-edge is enforced at target >=35 and must be reworked as one audited subsystem, not a blind Compose `Scaffold` + extra insets (double-padding/covered buttons risk). [Views edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
3. **P0 — file jobs and foreground services.** Both `FileJobService` and `FtpServerService` declare `dataSync`; `FileJobService` uses `startService` and executor/cancel; FTP service uses `startForegroundService`. Target >=35 introduces a combined **six-hour / 24-hour dataSync** background quota and `onTimeout(int,int)` recovery obligations; notification denial and background-start exceptions must be tested. [FGS timeout documentation](https://developer.android.com/develop/background-work/services/fgs/timeout).
4. **P0 — picker/SAF contract.** `FileListFragment` returns own-provider vs optional platform external-storage URIs and read/write/persistable/prefix flags according to action; providers allow remote/archive access. Preserve the URI *authority, document/tree shape, MIME, grant lifetimes and error states*, not just screen appearance. Review `FileProvider` streaming/seekable open and `FileSystemDocumentsProvider` capability flags. `MANAGE_DOCUMENTS` is signature-only; the standard APK must not declare it. [DocumentsProvider](https://developer.android.com/reference/android/provider/DocumentsProvider).
5. **P1 — predictive Back.** Manifest already enables back invoked callbacks, but `FileListFragment` has `OnBackPressedCallback` layers for breadcrumb, drawer, speed-dial, action mode. On targeting >=36 Android 16+ changes dispatch and default predictive animations. Test gesture preview/cancel and nested overlay unwind, don't merely replace a back icon. [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).
6. **P1 — large-screen resizability.** At target 37 the large-window orientation/resizability opt-out disappears (>=600dp smallest width according to platform guidance). Current drawer/list/grid/bottom bar and immersive viewer must survive runtime resize, split screen, landscape and TV even before Compose navigation. [Android 17 guidance](https://developer.android.com/blog/posts/prepare-your-app-for-the-resizability-and-orientation-changes-in-android-17).
7. **P1 — notifications and external open/share.** `POST_NOTIFICATIONS` requested by onboarding on API 33+, optional for granting, but foreground work still needs correct user-visible progress and cancellation. Retain `OpenFileActivity`, app defaults, grants and MIME, thumbnails (Coil PDF/video/exif), `SaveAsActivity` and external intent filters through Phase A–D. Removing internal `TextEditorActivity`/`ImageViewerActivity` in Phase E needs deliberate manifest/default-app routing tests. [Notifications permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
8. **P2 — other Android 17 changes.** Consider IME visibility after rotation, new global hardware keyboard shortcuts, Java reflection/static-final limitations, memory limits and cross-profile loopback behavior if relevant. Test native/root/Shizuku code rather than assuming no hidden API risk. [Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all), [targeting changes](https://developer.android.com/about/versions/17/behavior-changes-17).

## Gate A result / open blockers

- Git branch initial parent verified; exact final commit can be checked with `git log -1 --format=%P m3e/integration` or GitHub commit API.
- Files added are baseline documentation, a non-invasive static contract checker and branch-specific unsigned debug CI; no app code, viewer, theme or permission modifications.
- **Coverage gap:** no local SDK/NDK/ADB or clone; no actual screenshots/instrumented tests; no pre-existing test suite. Historical release CI passes; fresh debug/systemPicker results should be taken from the new branch's workflow when available.
- **Regression vs known failure:** source warnings and lack of test execution are baseline findings; every new test failure in Phase B onwards must be reproduced on this parent snapshot before being labelled a regression.
- **Gate status:** branch setup and inspectable preflight inventory complete; functional/test portion remains **environment-limited and unverified** until branch CI and representative device tests are available. Do not treat this note as proof that UI or permissions were exercised.
