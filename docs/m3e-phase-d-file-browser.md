# Phase D: file-browser workstream

## Provenance

Repository: VerdantGoosePath/VFiles
Branch: m3e/file-browser
Exact shared integration parent: fa8db3a069f34caa946c16828f2326d406af5634
No merge or push to integration or main. Integration agent handles shared-file reconciliation.

## Implemented browser

The default non-TV, non-external-picker browser now renders a lazy Compose file list/grid from the existing FileListViewModel, FileItem, LiveData, provider and file-job APIs. FileBrowserContent performs sorting and hidden filtering on Dispatchers.Default; lazy row keys are stable path identities. Coil's installed file/provider fetchers and thumbnail cache are reused through visible AndroidView ImageViews. Metadata, directory type, symbolic links, broken links, archive and encrypted indicators, read-only state, and per-item menus are represented.

FileBrowserChrome provides native Compose breadcrumbs, search/query/IME/debounce/close, list/grid and sort choices (including ascending, folders-first, per-path and hidden toggles) plus labelled selection/paste commands. FileBrowserCommandContext isolates and tests contextual eligibility; the Fragment still owns all provider operations and URI grants. Shift+arrow range selection is routed through FileItemSet. FileBrowserCreateMenu replaces the old speed dial but invokes the same create-file and create-folder dialog contracts, which Secondary UI may independently replace.

NavigationComposePane renders the real NavigationItemListLiveData hierarchy, including bookmarks, standard directories, internal/network/other storage, removable volumes and partitions, add storage, and settings. Navigation items dispatch to the original NavigateToRoot/NavigateTo/launchIntent handlers. Volume free-space subtitle calculation happens on Dispatchers.IO. The existing inset-aware NavigationRecyclerView stays measured and remains the TV/D-pad fallback.

FileBrowserJobs displays genuine concurrent file job status with determinate percentage where known, indeterminate loading otherwise, real detail text and per-job cancellation. Existing FileJobService owns errors/notifications. Ordinary file opening uses external ACTION_VIEW and chooser; explicit external ACTION_EDIT is available for eligible writable files. Archive cached-file opens also use external ACTION_VIEW without an unsolicited write grant.

## Deliberate compatibility fallback

The original RecyclerView/toolbar/selection mode remain active on Android TV and for external file-picker sessions, preserving established D-pad focus, picker result URI grants, CREATE_DOCUMENT filename/replace flows, keyboard shortcuts, and original bottom controls. Raw WindowInsets components (PersistentBarLayout, PersistentDrawerLayout, CoordinatorAppBarLayout, NavigationRecyclerView) are retained. The new Compose surfaces live within their existing host boundaries; do not remove legacy classes until all fallback callers are migrated and tested.

## Menu coverage

file_list.xml: search, view list/grid, sort name/type/size/last-modified, ascending, folders-first, per-path override, new task, navigate up/to, refresh, select all, show hidden, share, copy path, terminal, bookmark and shortcut. Compose duplicates key frequent actions; existing overflow covers all remaining commands.

file_item.xml: open with, external edit, cut, copy/extract-copy, delete, rename, extract, archive, share, copy path, bookmark, create shortcut, properties. Compose menu dispatches to existing methods and respects read-only/archive eligibility.

file_list_select.xml, file_list_pick.xml, file_list_paste.xml, and file_list_pick_bottom.xml remain as compatible picker/legacy action-mode contracts.

## Validation and open device QA

Feature branch workflow m3e-file-browser.yml runs SDK-free contract checks, assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, assembleSystemPickerDebug and testSystemPickerDebugUnitTest. FileBrowserCommandContextTest covers six command cases. FileBrowserComposeUiTest compiles instrumented search and view-mode tests; it requires a device/emulator to execute.

Not device-tested: local and remote provider traversal, storage denied/revoked, massive lists, thumbnail pressure, gesture/three-button nav, cutouts, IME, landscape/tablet freeform resizing, RTL, font 200%, high-contrast, TalkBack and TV D-pad. CI assembly is not runtime evidence. Native flexible app bar, full Compose picker, and reusable dialog visual replacements remain integration tasks.

## Shared-file overlaps

app/build.gradle, filejob/FileJobs.kt, res/menu/file_item.xml, filelist/FileListFragment.kt and browser layout hosts may intersect other agents. Secondary UI separately removes built-in viewer/editor classes; reconcile manifest and remaining call sites at integration. Preserve provider/file-job/security behaviour and the fallback picker until equivalent parity is device-verified.
