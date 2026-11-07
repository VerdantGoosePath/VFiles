# File browser: Material 3 Expressive design contract

This is the design review for the file browser, grounded in **Material 3 Expressive on Android 17 — agent knowledgebase** (revision 2026-11-06). The November 7 device screenshots showed a real usability failure, not just a styling preference: independent chips competed for attention; the app-bar file count was duplicated; an eight-entry sort popup covered files; every file showed a checkbox or **Select** CTA; 46–88dp icons sat inside hard gray squares; and selection actions were an unwieldy horizontally clipped ribbon. The later pass found the opposite problem: the Compose browser was still framed by a View toolbar and navigation drawer, so the screen read as M3E content inside an older shell.

## Screen architecture (final hardening)

The browser is one full-screen Compose surface, `FileBrowserScreen` (`filelist/FileBrowserScreen.kt`), hosted by `FileListFragment` through a single `ComposeView`. The fragment translates `FileListViewModel` state into three immutable models (`FileBrowserChromeState`, `FileBrowserContentState`, `FileBrowserNavigationState`) and implements `FileBrowserScreenActions`, so permission, read-only, archive and picker policy stay in the existing fragment/ViewModel/service code.

| Window | Navigation | Chrome |
| --- | --- | --- |
| < 600dp | `ModalWideNavigationRail(hideOnCollapse = true)` opened from the search bar's leading menu button | `AppBarWithSearch` + `ExpandedFullScreenContainedSearchBar` |
| ≥ 600dp | Persistent `WideNavigationRail`, expanded or collapsed (remembered by `FILE_LIST_PERSISTENT_DRAWER_OPEN`) | Same app bar, no menu button |

The navigation drawer is gone from the browser ([C32]: the drawer is deprecated in M3E; the wide rail replaces it). Rail entries are the original `NavigationItem`s (storage roots, standard folders, bookmarks, FTP server, settings), rendered by `VFilesNavigationRailContent` with `WideNavigationRailItem`; long-press edit, provider-aware checked state and off-main-thread free-space subtitles are preserved. The width breakpoint is the window width, not the device type ([I03]).

Android TV and the external SAF picker deliberately keep the legacy View UI (`legacyBrowserView`) for D-pad and URI-grant contracts.

## [V00] Primary job and hierarchy

**Job:** locate a file or folder, identify it correctly, and open, select, or operate on it without losing place.

1. **Content first:** directory entries, meaningful thumbnails, accurate names and file metadata; two density modes with remembered position by provider/path.
2. **Quiet tools:** one search bar, a headline location title with a breadcrumb trail, one sort button and a connected list/grid toggle. The item count is shown once, beside the sort button.
3. **Task-scoped commands:** creation is a single FAB menu; selection and paste use floating toolbars above the content, and selection swaps the app bar for a contextual bar.

One accent hierarchy per state; no decorative animations, gradients or special shapes on every file.

## [C03, C10, C17, C20, C27, C32, C34] Components

| Need | Implementation | Reason |
| --- | --- | --- |
| Search | `AppBarWithSearch` with a pill `SearchBar`; expanding opens `ExpandedFullScreenContainedSearchBar` with debounced (350ms) results from the existing search flow | Platform search pattern with predictive Back; no homegrown overlay |
| Location | `headlineMediumEmphasized` title, tonal breadcrumb trail, item summary | The current folder is the screen's headline |
| List/grid | Connected `ButtonGroup` toggle | Mutually exclusive view modes ([C03]) |
| Sort/options | `ModalBottomSheet` with segmented radio rows and switch rows | Eight settings never pop over the file list |
| List rows | Stable-key `LazyColumn` of `SegmentedListItem`s on `surfaceContainer`, 2dp seams, large outer / small inner corners, selected rows morph shape and take `secondaryContainer` | [C10] grouped list; the platform `surface` segment colour would vanish on the `surface` window background, so `VFilesListDefaults` lifts it one tonal step |
| Grid tiles | Adaptive `LazyVerticalGrid`, media-first tiles whose corner morphs on selection | No permanent Select buttons or gray backplates |
| Selection | Long-press or item menu **Select**; check badge replaces the thumbnail; contextual `TopAppBar` (count, select all, clear) + `HorizontalFloatingToolbar` (copy, cut, share, delete, plus extract/archive in a menu) | Selection is a mode, not a permanent checkbox column ([C21]) |
| Creation | `FloatingActionButtonMenu` (file / folder) with `ToggleFloatingActionButton`, scrim and Back dismissal | One primary creation launcher, hidden during selection/search/paste and read-only/picker states |
| Paste | Vibrant `HorizontalFloatingToolbar` with the paste action as its FAB ([C34]); the label states the item count and "Paste here" or "Read-only" | The destination is the folder on screen |
| Jobs | Pill with aggregate determinate/indeterminate progress, opening a sheet of per-job progress and cancel | Honest feedback for long transfers |
| Loading/empty/error | `LoadingIndicator`; empty and error states in a `MaterialShapes.Cookie9Sided` container with one recovery action | Calm, specific states ([V03]) |

## Visual grammar and token ownership

- **Color:** semantic roles only (`surface`, `surfaceContainer*`, `secondaryContainer`, `tertiaryContainer`, vibrant toolbar roles). Selection uses `secondaryContainer`; badges use `tertiaryContainer`. No ad-hoc RGB.
- **Type:** emphasized roles for the location title, selection count and section headings; `bodyLarge` file names, `bodyMedium` metadata.
- **Shapes:** grouped list with softened outer edges; selected rows and tiles morph to a larger corner; search and toggles fully rounded; expressive `MaterialShapes` only for empty/error states.
- **Motion:** `MotionScheme.expressive()` from `VFilesTheme`; spatial springs for shape morphs and rail width/spacing; effects specs for colour. No per-row entrance animation. Reduced motion flows through `VFilesTheme`.
- **Spacing:** 16dp gutters, 112dp extra bottom padding so the FAB or floating toolbars never trap the final row, 48dp minimum targets.
- **Rows:** supporting content that mixes text with badge chips reports a single baseline (`Modifier.singleLineBaseline()`), so `ListItem` keeps two-line padding instead of switching to three-line.

## States and interaction contract

- **Idle:** search bar, location, sort and entries visible immediately.
- **Search:** full-screen search with IME; results reuse the provider-aware list; Back or close restores browsing.
- **Select:** contextual app bar and floating toolbar; row click toggles selection; operations still pass write/read-only/archive policies; FAB hidden.
- **Paste:** real source count and destination restriction; cancel clears the source; FAB hidden.
- **Read-only/provider:** no create or destructive commands where disallowed.
- **Loading/empty/error:** truthful status and one recovery action; scroll state kept per path and mode.

## [Q00] Verification

- `FileBrowserScreenPreviewTest` (Robolectric, native graphics) renders the real `FileBrowserScreen` to `app/build/m3e-previews/` for phone light/dark, grid, selection with a running job, paste, FAB menu, empty, error, and tablet expanded/collapsed rail in light and dark. These are design-review renders, not golden tests. Robolectric cannot capture the modal rail's dialog window, so the phone modal rail is not rendered.
- `FileBrowserComposeUiTest` and `FileBrowserScreenshotSmokeTest` compile as instrumentation tests; they were not run on a device in this pass (the local emulator could not run without KVM).
- Still required on devices: 320/360/393/600/840/1200dp, 1.0/1.3/2.0 font scale, dynamic/high-contrast colour, RTL, IME/nav insets, TalkBack/keyboard, predictive Back, and real local/remote/archive/permission-denied folders.

**Dependency baseline:** `minSdk 24`, Compose Material3 `1.5.0-rc01` (the earlier `1.5.0-alpha23` pin existed only to keep `minSdk 23`).
