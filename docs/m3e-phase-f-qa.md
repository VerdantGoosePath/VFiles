# Phase F — Android 17 and Material 3 Expressive final QA handoff

> **Current status, 2026-11-07 final continuation:** VFiles now targets **API 37 by default**. The earlier API34 "blocked" assessments below are preserved only as historical findings and are superseded by the latest section at the end. This document is an engineering audit, not a blanket claim that every surface is visually certified.


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


## Continued M3E redesign — 2026-11-07 (follow-up)

This section updates the earlier *source-only* audit; the original migration risk register remains below as historical context. Design decisions were checked against the supplied `M3E_ANDROID17_AGENT_KNOWLEDGEBASE.md` ([P00], [I01]–[I06], [V00]–[V05], [C37], [M06]–[M07], [Q00]).

- **No M2 flash at startup:** All 29 AndroidManifest activity theme declarations now start from the Material 3 base. `CustomThemeHelper` recognizes already-M3 themes when calculating stored color presets; no duplicated `.Material3` suffix. AppCompat hosts are kept for required fragments/SAF/contracts, but their visible styling is now from the same M3 family as their Compose children.
- **View/Compose material coherence:** All app View themes now specify 48dp minimum Material touch targets; M3 app bars use paired quiet `colorSurface/onSurface` instead of the old saturated primary bar. Material shape large corners 20dp, navigation subtitles 12sp, and View Toolbar subtitles use sp. Existing Material text fields are still valid [C37] controls and inherit M3 styling.
- **Remote credentials/storage:** FTP, SFTP, SMB and WebDAV forms retain fully validated provider callbacks, credential handling and connection-in-progress blocking, while gaining a 760dp width cap, consistent field rhythm, 48dp actions and a two-level action footer that does not squeeze three buttons onto a 320dp/200% type row.
- **File properties:** Seven existing provider/metadata fragments are preserved, but the legacy View TabLayout has been replaced with a native `PrimaryScrollableTabRow` backed by the existing ViewPager. Read-only metadata uses soft M3 container shapes rather than repeated outlined input chrome. This is **Compose/View interoperability**, not a wholesale property-data rewrite.
- **Appearance:** The prior arbitrary color-strip preview was replaced with an actual small Material Expressive file-browser composition (search, selected folder, tonal surfaces). The preview is decorative and excluded from screen-reader interaction. For API34+ preset/brand palettes, Material Color Utilities now follows the platform accessibility contrast setting. Dynamic schemes are still provided by the OS when available.
- **File-browser usability:** The bottom selection/paste dock now reserves measured space, preventing 200% text, IME and localized action labels from covering the last file. Navigation rows enforce at least 56dp, avoid advertising long press where no action exists, and give loading feedback while remote storage subtitles resolve. Reduced platform animation settings disable legacy subtitle crossfades.
- **Rendered evidence pipeline:** `.github/workflows/m3e-visual-emulator.yml` runs actual `:app:connectedDebugAndroidTest` on an API35 emulator and uploads JUnit/instrumentation plus light/dark browser-chrome screenshots. This is *not* Android17/API37 runtime coverage. Existing branch smoke workflow still builds normal/systemPicker flavors, instrumentation APKs and runs JVM tests.
- **First device-run finding:** API35 run [#42368832661](https://github.com/VerdantGoosePath/VFiles/actions/runs/42368832661) **FAILED** when `createComposeRule()` launched AndroidX's bare `ComponentActivity`, which the app's strict `CustomThemeHelper` initialization rejected. Commit `20e756fd55d18df49a2a67c259b7daa0e4dc61fa` allows exactly this test-host class only in debuggable builds; the release invariant still rejects uninitialized activities. Recheck on a new run; do not label this test fixed until it passes.
- **Compilation evidence:** Follow-up M3E revisions through `303d9d89127876e93c856a1f815979faeb3d8f89` passed both standard and optional systemPicker CI. Later commits require their own CI verification; Action results are authoritative.

**Not yet certified holistically M3E:** File-property content tabs, credential inputs and numerous operational dialogs use Material3-themed View interop rather than native Compose; the broad application routes were not all visually photographed or manually navigated. Automated screenshots only cover a representative browser-chrome state. A 16+/20 [Q00] score, WCAG color audit, 200% font/RTL/TalkBack testing across every surface, high-res tablet and freeform window testing, performance measurements, and API37 runtime tests are still needed. Avoid claiming release-ready, completely Compose-first, or visually final until these are performed. **Android17 target remains blocked at targetSdk34** by foreground-dataSync lifecycle constraints identified above; changing UI alone does not solve the filejob/FTP contract.


## Android 17 / expressive continuation — 2026-11-07 (third pass)

This addendum supersedes earlier *unresolved* service-type and test-infrastructure findings without rewriting their historical evidence. Source of design authority: the supplied guidebook [P00], [I00]–[I06], [V00]–[V05], [C17], [C21], [C32], [M05]–[M07] and [Q00]. Platform foreground-service semantics were cross-checked against <https://developer.android.com/develop/background-work/services/fgs/service-types> and <https://developer.android.com/develop/background-work/services/fgs/timeout>.

**Platform and service contracts.** Incoming FTP hosting is a connected external-device/network interaction, **not** a bounded foreground data sync. `FtpServerService` now declares `connectedDevice`, together with the normal `CHANGE_NETWORK_STATE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` manifest permissions; exception text containing network credentials or private hostnames is never logged or displayed verbatim. `FileJobService` remains `dataSync`, but now responds to the Android 15+ six-hour timeout by promptly calling `stopSelf()`, cancels worker futures during service destruction, and uses `START_NOT_STICKY` because in-RAM file jobs cannot be resumed after a process kill. **Partial transfers still need user review/retry, and neither service has been validated during six-hour quota expiry on an actual API37 device.** Do not claim this is lossless/background-restart handling.

**Theme and interaction quality.** File-property loading now uses native Compose `LoadingIndicator` with a spoken/visible loading label; provider errors offer an explicit retry instruction, and its directory counting no longer blocks an IO traversal while waiting for main thread or logs private filesystem exceptions. Properties with actions are focusable for D-pad/keyboard users. Delete and replace dialogs carry explicit titles and apply the current Material3 View-theme error color to destructive confirmation. The former reflective subtitle crossfade is deliberately removed: file counts update immediately, with no invasive/fragile View-field animation.

**M3E version tradeoff.** The current app still uses `androidx.compose.material3:material3:1.5.0-alpha23`, not the guidebook's preferred newer `1.5.0-rc01`, because the repository preserves `minSdk 23` and later M3 libraries acquire `material3-ripple` with `minSdk 24`. This is intentional backwards compatibility and a blocker to *latest-version* conformity; do not force manifest overrides to hide a possible runtime crash on API23. All actual Expressive APIs used compile against the pinned dependency.

**Android17 build gates.** With `-PtargetApi37` the Gradle build now compiles at `targetSdk 37` (shipping default remains 34). The first complete normal, systemPicker and target37 build/JVM CI sweep passed: [run 42371194914](https://github.com/VerdantGoosePath/VFiles/actions/runs/42371194914). The release target is deliberately unchanged pending physical-device process death, permission/URI grants, file transfer/FTP background, large-window and predictive Back testing. This opt-in is a migration test, **not** permission to ship target37 yet.

**Rendered-device evidence.** `.github/workflows/m3e-visual-emulator.yml` now executes instrumented tests and screenshots from actual Settings and FTP activities and a file-browser Compose surface on Android API35. If the phone configuration passes, it also changes emulator display to **840dp width, 200% font** and runs the same tests with the API37 target canary, uploading distinct PNGs and logcat evidence for both. The automated screenshot suite does **not** yet cover every modal, real file-provider error/selection state, or TalkBack interactions. Early runs failed because `CustomThemeHelper` rejected AndroidX's bare Compose-test activity. The latest fix permits only the exact `androidx.activity.ComponentActivity` class declared in the test APK; future production activities must still initialise their app theme. CI must confirm the latest emulator run before considering the fix verified.

**Current acceptance judgement:** Further work and device sign-off remain necessary before the word *holistic* is justified. The full [Q00] rubric requires 320/360/393/600/840/1200+dp, RTL, light/dark/dynamic/high-contrast, reduced motion, TalkBack/keyboard, actual Android17 backgrounds/permissions, low-end scroll performance and complete route-by-route visual review. The tests now enable collection of representative rendered evidence, not automatic fulfilment of that full matrix.

## Default API37 + verified M3E implementation — 2026-11-07 (latest)

The user explicitly accepted deferring lossless, resumable six-hour dataSync transfer work as a low-priority edge case. The **default** Gradle `targetSdk` is now **37**, matching `compileSdk 37`; there is no longer an opt-in `-PtargetApi37` gate. `minSdk 23`, FTP's `connectedDevice` foreground-service classification, `FileJobService.onTimeout()` and `START_NOT_STICKY` remain intact. Nothing here implies six-hour dataSync file-job restoration is solved. Do not conflate the API35 emulator OS with the API37 *target*: the instrumented device runs Android 15 (API35), while the tested app targets Android17 (API37).

The reported earlier theme-lookup bug was real: `Resources.getResourceName()` includes a package/type prefix, so checking `baseThemeName.startsWith("Theme.MaterialFiles.Material3")` incorrectly doubled the `.Material3` resource suffix. `CustomThemeHelper` now checks the final resource name after `/`. Both lifecycle theme owners accept the test-manifest's **exact** bare `androidx.activity.ComponentActivity` while maintaining AppActivity initialization requirements for real app screens.

Further guidebook-informed M3E changes ([I03], [I04], [I05], [V00], [C21], [C22], [C32], [Q00]): native `SegmentedListItem` groups for browser sort radio choices and options with a single action owner; list content bounded to 960dp in expanded windows; long-press semantics only where a file actually supports selection; truthful aggregate transfer percentages (indeterminate when any concurrent job is indeterminate), with unit coverage; localized progress/cancel text; View-toolbar subtitle mapped to the Material3 `BodySmall` typography role; adjacent-only property pager preloading rather than constructing all seven provider metadata tabs. The properties and remote connection editor detail surfaces still use M3-themed View interoperability for functional parity, not full Compose rewrites.

### Verification completed on this continuation

- **[GitHub Actions 42381591355](https://github.com/VerdantGoosePath/VFiles/actions/runs/42381591355)**: **SUCCESS** at app commit `b0af338c053a787c3de2f9f1d8e4eebd603b159a`, including SDK-free static contracts, API37 standard debug assembly, debug instrumentation APK compilation, JVM unit tests, optional `systemPicker` debug assembly and JVM unit tests.
- **[GitHub Actions 42381591359](https://github.com/VerdantGoosePath/VFiles/actions/runs/42381591359)**: **SUCCESS** at the same source commit. Runs real Compose/instrumented UI tests on the API35 emulator and, after the normal phone display, again at 840dp wide with 200% font, using the default API37 target. Screenshot/AndroidTest evidence artifact `m3e-api35-emulator-ui-evidence` ID `13578420623` (350,540 bytes) uploaded successfully.
- The emulator workflow had initially failed because multi-line `script:` YAML statements ran in isolated `/bin/sh` processes and later because the test activity was rejected by the night-mode lifecycle guard and actual FTP/Settings Fragment commits had not yet completed at assertion time. These defects have been corrected, and the latest run is green.
- Android lint is still advisory; the prior comprehensive report had **103 errors and 960 warnings**, mostly inherited legacy compatibility debt; a green preflight workflow does not mean lint is clean. Lint triage, visual inspection of the captured PNGs, contrast tools, TalkBack/RTL, 320dp landscape, high-contrast, real-device API37 runtime and performance testing remain explicit acceptance tasks.

**Acceptance:** Verified functional Compose UI integration for the automated scenarios, not unconditional holistic M3E certification. [Q00] requires representative coverage of all routes and states, dynamic and dark/pure-black schemes, keyboard/assistive technology, touch target sizes and performance. This QA branch is **not merged** into `m3e/integration` or `main`.
