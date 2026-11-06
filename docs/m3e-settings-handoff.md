# M3E Settings workstream — isolated handoff

Parent branch: `m3e/integration`. **Exact parent SHA:** `fa8db3a069f34caa946c16828f2326d406af5634`.

Owned branch: `m3e/settings`. Do not merge this branch into integration automatically; the integration agent should reconcile shared theme work.

## Persistence inventory (all pre-existing `Settings.kt` keys retained)

Existing typed `SettingLiveData` and resource-derived preference key/default values are unchanged. In particular, enum values are persisted as **ordinal strings**; every enum declaration/order remains unchanged. Parcel-backed lists and paths use `ParcelValueSettingLiveData` and retain their representation. No new `SharedPreferences` store was introduced.

| Setting | Backing type / semantics | Screen and side effects |
|---|---|---|
| `STORAGES` | `ParcelValueSettingLiveData<List<Storage>>` | Settings summary; existing `StorageListActivity` manages storage visibility, ordering and configured mounts. |
| `FILE_LIST_DEFAULT_DIRECTORY` | Parcelled `java8.nio.file.Path` | Settings folder picker invokes `FileListActivity.OpenDirectoryContract` via Fragment ActivityResult; update only on successful result. |
| `FILE_LIST_PERSISTENT_DRAWER_OPEN` | Boolean | Browser/navigation control, left in its existing owner (not falsely presented as a settings option). |
| `FILE_LIST_SHOW_HIDDEN_FILES` | Boolean | Browser action; unchanged. |
| `FILE_LIST_VIEW_TYPE` | Enum ordinal string | Browser list/grid; unchanged; path overrides `PathSettings.getFileListViewType` preserved. |
| `FILE_LIST_SORT_OPTIONS` | Parcelled `FileSortOptions` | Browser sorting; unchanged; per-path overrides preserved. |
| `CREATE_ARCHIVE_TYPE` | Resource-name string through `ResourceIdSettingLiveData` | Archive creation dialog, unchanged. |
| `FTP_SERVER_ANONYMOUS_LOGIN` | Boolean | Existing FTP server screen; retained intact through Settings network link. |
| `FTP_SERVER_USERNAME` | String | Existing FTP screen, never rendered in main Settings. |
| `FTP_SERVER_PASSWORD` | String | Existing FTP screen; **never rendered, logged or summarized by the Compose settings screen**. |
| `FTP_SERVER_PORT` | Integer | Existing FTP screen. |
| `FTP_SERVER_HOME_DIRECTORY` | Parcelled Path | Existing FTP screen and directory picker. |
| `FTP_SERVER_WRITABLE` | Boolean | Existing FTP server screen. |
| `THEME_COLOR` | Enum ordinal string (`ThemeColor.DYNAMIC` last) | Expressive swatches, wallpaper sampling, `CustomThemeHelper.sync()` for legacy View surfaces; live preview from real `VFilesTheme` resolver. |
| `MATERIAL_DESIGN_3` | Boolean | Obsolete legacy key retained inert. Modern view theme no longer obeys its false value; removed from all user-facing settings. |
| `NIGHT_MODE` | Enum ordinal string (5 old values kept) | Expressive single choice; `NightModeHelper.sync()` owns recreation; system/time/battery modes still supported. |
| `BLACK_NIGHT_MODE` | Boolean | Expressive toggle and true-black surface preview; `CustomThemeHelper.sync()` handles Views. |
| `FILE_LIST_ANIMATION` | Boolean | Expressive switch, file-list animation original consumer unchanged. |
| `EDGE_TO_EDGE` | Boolean | Exposed while build targetSdk34 actually permits opt-out; hidden when targetSdk≥35 on Android≥35, with stored data intact. Original theme synchronization retained. |
| `FILE_NAME_ELLIPSIZE` | `TextUtils.TruncateAt` enum ordinal string | Expressive 4-choice dialog; same persisted enum. |
| `STANDARD_DIRECTORY_SETTINGS` | Parcelled `List<StandardDirectorySettings>` | Expressive switch group, preserving the old ID-to-settings override update logic. |
| `BOOKMARK_DIRECTORIES` | Parcelled `List<BookmarkDirectory>` | Expressive bookmarks screen; add/edit/delete via original contracts, persisted reordering by `BookmarkDirectories.move`. |
| `STORAGE_VOLUME_CUSTOMIZATIONS` | Parcelled list | Existing storage management; retained untouched. |
| `FILE_OPEN_DEFAULT_APPS` | Parcelled `List<FileOpenDefaultApp>` | Expressive external-app chooser; `FileOpenDefaultApps.setPackage`, `ACTION_VIEW` with `content:` FileProvider URI + MIME. |
| `ROOT_STRATEGY` | Enum ordinal string | Expressive choice guarded by running-file-job count confirmation. |
| `ARCHIVE_FILE_NAME_ENCODING` | Charset name string | Expressive charset dialog using `Charset.availableCharsets()`. |
| `OPEN_APK_DEFAULT_ACTION` | Enum ordinal string | Expressive Install/View/Ask radio choice. |
| `SHOW_PDF_THUMBNAIL_PRE_28` | Boolean | Expressive switch only before API28, preserving prior visibility condition. |
| `READ_REMOTE_FILES_FOR_THUMBNAIL` | Boolean | Expressive switch; remote thumbnail reading remains opt-in. |
| `ONBOARDING_COMPLETED` | Boolean | Onboarding process, untouched. |

Locale is a separate `LocaleManagerCompat` / AppCompat persistence contract, **not** a new `Settings.kt` key: API33+ launches `ACTION_APP_LOCALE_SETTINGS` and refreshes displayed locale on return; pre33 uses `LocaleConfigCompat`, `LocaleListCompat`, and the existing `SettingsActivity.setApplicationLocalesPre33` recreation sequence.

## Migrated route ownership

- `SettingsActivity` → `SettingsFragment` → `ExpressiveSettingsScreen` (Compose, no XML Preferences).
- `StandardDirectoryListActivity` → `StandardDirectoryListFragment` (Compose, original typed overrides).
- `DefaultAppsActivity` → `DefaultAppsFragment` (Compose, external package choices only).
- `BookmarkDirectoryListActivity` → `BookmarkDirectoryListFragment` (Compose, accessible reorder buttons).
- Storage management, FTP server operation/settings and bookmark edit dialogs keep their existing activities/contracts; these involve storage/network functionality beyond this settings entry/selection workstream.
- About screen remains the original `AboutActivity` accessed from settings.

## Regression verification

GitHub Actions `.github/workflows/m3e-settings.yml` checks repository baseline constraints, verifies legacy entrypoint removal, compiles the app and instrumentation tests, and runs JVM unit tests. Use:

```sh
python3 scripts/check_phase_a_contracts.py
./gradlew --no-daemon --warning-mode all :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
# On an attached Android device/emulator:
./gradlew :app:connectedDebugAndroidTest
```

Instrumentation source `ExpressiveSettingsTest` checks a visible appearance preview, absence of the obsolete Material Design 3 setting, and persistence after Activity recreation. UI/device matrix still needs actual execution: 360/393/600/840/1200dp, portrait/landscape, keyboard/TV, 1x/1.3x/2x fonts, RTL, light/dark/wallpaper seed/AMOLED/high contrast, TalkBack touch targets, all ActivityResult paths, app languages and process death. A compile pass alone is not evidence of those behaviors.

## Integration conflict notes

Shared file deliberately edited: `theme/custom/CustomThemeHelper.kt`. It now treats M3 as always-on for *View* themes and applies wallpaper palette only when `ThemeColor.DYNAMIC` was selected; it retains old stored switch data. Other agents may be migrating the View root; reconcile any edits to this helper carefully. No Gradle, browser or secondary-viewer/editor files changed.

The other old preference-fragment classes (for example standalone FTP preference widgets) remain because some storage/network screens still use them. They do **not** provide an alternate entry into the now-removed main XML settings route.

Bookmark ordering is kept with explicit move-up/move-down buttons rather than the former drag-handle gesture, increasing accessibility at the cost of drag interaction. The actual running tests and emulators, rather than this note, determine release readiness.
