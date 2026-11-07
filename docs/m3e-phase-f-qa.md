# Phase F — Android 17 and Material 3 Expressive final QA handoff

**Audit date:** 2026-11-07. **Branch:** `m3e/final-hardening`. **Exact integration base:** `eb0d9f4c8ccbf26ef4359235b8b4994f84ae6c02`. Do not merge this branch automatically. This is a source-and-CI audit, **not a device sign-off**.

## Integration provenance

The integration base has all three feature branch tips in its history (GitHub compare: behind 0, merge base equal to feature head):

| Feature | Integrated feature tip |
| --- | --- |
| Settings | `ef68f816884e3e9d6fe687a6ce299970f46606fb` |
| Secondary UI | `7dcfe913654bde6620b411c89a646a19622867d4` |
| File browser | `54032ff769cc7a2f80e2e238b50f023000096036` |

Latest prior integrated baseline CI: [GitHub Actions #42337832028](https://github.com/VerdantGoosePath/VFiles/actions/runs/42337832028), successful standard debug, debug Android test **compilation** (not device execution), JVM unit tests, optional systemPicker debug and JVM tests. New Phase F workflow `.github/workflows/m3e-preflight.yml` additionally runs `scripts/check_phase_f_contracts.py` and advisory lint on pushes to this QA branch.

## Surface/route inventory and migration status

Inventory derived from `app/src/main/AndroidManifest.xml`, active fragments, layouts, menu resources, providers, and flavor overlay. **Compose ownership is not equivalent to every surface being fully migrated.**

| User-visible surface / contract | Current owner | Status and exception |
| --- | --- | --- |
| File browsing, search, sort, list/grid, selection, create FAB, paste and job progress | `FileListActivity` / `FileListFragment` hosting `FileBrowserChrome`, `FileBrowserContent`, `FileBrowserCreateMenu`, `FileBrowserJobs` | Native Compose M3E islands; existing View toolbar/drawer and file/provider LiveData retained |
| Local/storage roots, bookmarks, drawer | `NavigationFragment` / `NavigationComposePane` | Compose hierarchy for storage destinations; these are not permanent top-level app tabs; TV/picker View fallback intentionally retained |
| Settings, color/night/AMOLED previews, toggles, default apps, standard directories, bookmarks | `SettingsFragment`, `ExpressiveSettingsScreen`, dedicated Compose list fragments | Native segmented preference groups, flexible app bar, typed persistent settings; selected in-flight dialogs now restored on rotation |
| FTP server control/configuration | `FtpServerFragment`, `ExpressiveFtpServerScreen` | Compose. Editable port validated 1–65535; passwords not retained in SavedState |
| Storage list/edit/remove; FTP/SFTP/SMB/WebDAV configuration | `StorageListFragment` Compose; `Edit*ServerFragment` XML/MDC forms | Functional backend and validation retained; **forms remain View UI**, not fully M3E |
| Properties: Basic, POSIX permissions/SELinux, EXIF/image, audio/video metadata, APK, checksums | `FilePropertiesDialogFragment` + ViewPager/TabLayout and specific tab fragments | Functional, **legacy View tabs and dialog**; review high text scale/RTL/contrast on device |
| Onboarding/permissions | `OnboardingActivity` Compose + permission rationale dialogs | Compose onboarding; legacy rationale dialogs still interop |
| About, licences | `AboutFragment` Compose + retained licences dialog | Native About with quiet app identity accent |
| File job conflict/error, archive password, create/rename/archive dialogs | Existing DialogFragments | Retained operational interaction with View-themed widgets; not yet a full Compose/M3E conversion |
| File action `ACTION_VIEW`, `ACTION_EDIT`, Open with/Open as, default external apps | `OpenFileActivity`, `EditFileActivity`, `ExternalFileOpening`, `FileProvider` | Retained. **No internal image/media viewer or text editor** |
| Save As from external VIEW/SEND | `viewer.saveas.SaveAsActivity` | File manager handoff, not an internal viewer; avoid duplicate picker launch after Activity recreation |
| SAF/picker: GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE | Exported `FileListActivity` + legacy picker UI, file/provider URI grants | Retained; systemPicker is opt-in `-PsystemPicker`, only its manifest overlay declares signature-only MANAGE_DOCUMENTS |
| External file shares and OS documents | Non-exported `FileProvider` granting URIs; exported `FileSystemDocumentsProvider` guarded by `MANAGE_DOCUMENTS` | FileProvider and DocumentsProvider authority strings derive from application ID; grants must be reverified on actual devices |
| Android TV/D-pad | `FileListFragment` with RecyclerView and Compose creation menu | Intentional exception for remote focus/navigation; **retain** `file_list_fragment_speed_dial_include.xml` because it is now the Compose FAB host |
| Foreground copy/move/delete/extract and FTP server | `FileJobService`, `FtpServerService` | See Android 17 blocker below |

Manifest additional public entry points: launcher and archive/directory VIEW; FTP management and Quick Settings preferences; application Settings; external `SaveAsActivity` VIEW/SEND; QS tile service exported with `BIND_QUICK_SETTINGS_TILE`. Dialog activities, editing/trampoline activities and storage editors are internal where no intent filters require exports.

## Phase F changes (source-level)

- Avoid exposing obsolete directory entries while the next path/sort is preparing: keyed nullable asynchronous sort state distinguishes pending from truly empty results; preserve stable lazy-item path keys and thumbnails.
- Prevent search-loading null-list toolbar crashes in View interop and avoid raw remote/provider stack-trace logging there.
- Strip duplicate checkbox and switch accessibility nodes; give selection actions horizontal scroll under large text; use native RTL-auto-mirrored Back icon; fix selected sidebar subtitle foreground pairing.
- Restrict FTP listening port input to 1–65535; keep non-secret username/port drafts and selected dialog on rotation while avoiding saved-state persistence of typed secrets.
- Prevent duplicated `CREATE_DOCUMENT` launches in the Save As activity after recreation; restore existing ActivityResultRegistry result handling.
- Always release file job tracking and wake locks after unexpected job failure and show a sanitised high-level error instead of raw throwable details.
- Replace text-only storage-add glyph with a theme-tinted vector; remove orphaned old speed-dial Kotlin classes, unused menu and third-party dependency **without deleting the current Compose FAB layout**.
- Make release workflow install API37 (instead of mismatched API36); extend QA branch Actions, lint and JVM regression/static contracts.

## Android 17/API 37 gate — **BLOCKED for target37**

`compileSdk = 37`, build tools 37.0.0, `minSdk = 23`, and **`targetSdk = 34` remains deliberately unchanged**. AGP/Kotlin already compile against API37; Material3 `1.5.0-alpha23` is pinned for minSdk23 because later ripple artifacts require minSdk24. Do not silently upgrade the Compose Material3 version or reduce supported devices.

**Blocking evidence:** `FileJobService` and `FtpServerService` declare `foregroundServiceType="dataSync"`. Neither implements API35+ data-sync six-hour shared background budget/timeout recovery (`Service.onTimeout`, visible user continuation, transition to approved user-initiated jobs where appropriate). `FileJobService` also stores active/pending tasks in static and instance RAM and uses `START_STICKY`; killed processes cannot reconstruct a queued operation safely. An abrupt platform stop can interrupt multi-gigabyte transfers and remote writes. Raising target37 before a staged, resumable architecture and on-device timeout validation is a **functional regression**. FTP server indefinite foreground operation needs a platform-compliant design, not a fake timeout catch or indiscriminate FGS exemption. Confirm restart policy, notification permission decline, cancellation, concurrent jobs, 6h aggregate budget and user-visible recovery before attempting target37. Do not imply that compileSdk37 alone establishes Android17 target compatibility.

Other contracts to validate on devices: edge-to-edge, clear/light system bar contrast under 3-button/gesture, display cutout/IME, runtime resizing ≥600dp including freeform and split-screen, predictive Back/cancel, permission denial paths, persistent SAF grants, export/URI grant/chooser/edit intent roundtrips, FTP authentication/anonymous mode, remote SFTP/SMB/WebDAV host key/certificate/error handling, Android TV focus.

## M3E acceptance review (source findings vs required rendered review)

**Strengths (verified in source):** `VFilesTheme` installs `MaterialExpressiveTheme` + `MotionScheme.expressive()` with reduced-motion fallback; 30 default typography roles including emphasized variants; eight shape roles; dynamic HCT/preset/light/dark/AMOLED schemes and semantic success/warning/info; native `SegmentedListItem` settings; flexible top bars; native FAB menu; bottom sheet sorting; restrained neutral/tonal file rows and prominent selected-state contrast. Theme preview is composed from the same real palette resolver. File list intentionally prioritises content, avoiding exaggerated hero animation.

**Incomplete, not suitable for claiming fully holistic M3E:** root Activity hosts are still `AppCompatActivity`/XML themed while Compose regions install Expressive separately; the file manager retains a View app bar/drawer and some View-selected action UI, remote forms and properties are MDC/View-based, and several operational dialogs are older. The combination is not yet visually proven cohesive; in particular the properties tabs, credential forms, orientation behaviour and 200% font scaling could still look like M3E was attached to an older interface. No app screenshots, color contrast meter, TalkBack recordings or frame-time instrumentation were available in this task. **Visual design acceptance is provisional, not passed.**

**M3E-native component exceptions:** The file manager's storage/path roots are hierarchical sources rather than 3–5 application destinations, so using `ShortNavigationBar` just to meet a checklist would misrepresent information architecture. TV and external picker intentionally retain legacy View rendering for keyboard/remote and SAF contracts. Functional interoperability takes precedence over cosmetic replacement, but each exception still needs a device parity review.

**Guidebook compliance:** decisions reviewed against [P00], [I00]–[I06], [V00]–[V05], [C37], [M06]–[M07], [Q00]. A [Q00] score out of 20 is **not assigned** without rendered screens, representative dynamic colors, 200% font, TalkBack and hardware focus tests. Scoring solely from nominal component usage would falsely certify usability.

## Verification and release matrix

**Automated pipeline commands** (execute on `m3e/final-hardening` in GitHub Actions):
```bash
python3 scripts/check_phase_a_contracts.py
python3 scripts/check_secondary_ui_contracts.py
python3 scripts/check_m3e_integration_contracts.py
python3 scripts/check_phase_f_contracts.py
./gradlew --no-daemon --warning-mode all :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon --warning-mode all -PsystemPicker :app:assembleSystemPickerDebug :app:testSystemPickerDebugUnitTest
```
`assembleDebugAndroidTest` compiles instrumentation tests; **it does not run them on an emulator/device**. Lint is currently advisory (`continue-on-error`) until View-era findings are triaged; do not conflate the workflow's success with a zero-warning lint run.

**Manual/device tests not run:** instrumented Compose/UI tests; screenshot differences at 320/360/393/600/840/1200dp and 320dp landscape; portrait/landscape/freeform/split/fold; font 100/130/200%; high contrast/dynamic/color seed/pure black; RTL/long names; TalkBack/Switch Access/keyboard/mouse/TV remote; predictive Back cancel; status/nav/IME/cutouts; copying/moving/deleting 10k files, large remote directory and offline search; drag/reorder; remote authentication errors; SAF URI provider access from a second app; live long file jobs and process death; frame metrics (60/120 Hz), memory, thumbnail/slow-network performance. **No device or emulator coverage claimed.**

### Manual acceptance and integration merge rechecks

1. On a device, navigate local/SAF/FTP/SFTP/SMB/WebDAV and archives; test read-only and credential failures, operation retries, progress and cancellation, job rotation and process kill/relaunch, root and notification permission declines.
2. Verify file picker results for every requested intent, multiple selection, persistent URI grants and systemPicker platform-signed path; share, external VIEW/EDIT/Open-with/default-app roundtrip, Save As after rotation; confirm no in-app viewer/editor.
3. Inspect 393dp and 840dp screenshot pair for each major route at 100% and 200% text, dynamic/light/dark/AMOLED/RTL; carefully review View remote forms, properties tabs, dialog toolbars, TV and legacy picker.
4. Confirm selected/unchecked semantics, 48dp controls, fully visible action bars, TalkBack focus, keyboard D-pad order, screen-reader labels and correct Back precedence with IME, drawers and bottom sheets.
5. Benchmark large scroll, thumbnail memory and remote IO before making performance claims. No full-screen blur was introduced in Phase F. Compare against integration baseline if device metrics become available.
6. Recheck SDK37 target eligibility only after foreground service budgets, user-initiated/resumable transfers, FTP lifecycle and process-death policy are reworked. Preserve minSdk23 unless owner explicitly approves a support change.

## End-user change summary

File browsing and settings keep the native Expressive appearance while improving accessibility, recovery from rotations, stability during directory searches, FTP port entry validation, and error privacy. Creation, external file opening, file metadata, bookmarks, archives and storage integrations remain in place. Android 17 **compile** support is retained; API37 **target** adoption remains blocked by foreground service lifecycle compatibility and requires further engineering and device testing.
