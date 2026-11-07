# File browser: Material 3 Expressive design contract (Phase D)

This document is the design review for `m3e/file-browser`, grounded in **Material 3 Expressive on Android 17 — agent knowledgebase** (revision 2026-11-06). The November 7 device screenshots showed a real usability failure, not just a styling preference: independent chips competed for attention; the app-bar file count was duplicated; an eight-entry sort popup covered files; every file showed a checkbox or **Select** CTA; 46–88dp icons sat inside hard gray squares; and selection actions were an unwieldy horizontally clipped ribbon.

## [V00] Primary job and hierarchy

**Job:** locate a file or folder, identify it correctly, and open, select, or operate on it without losing place.

1. **Content first:** directory entries, meaningful thumbnails, accurate names and optional file metadata; two density modes with remembered position by provider/path.
2. **Quiet tools:** path trail, one search affordance, a single list/grid switch, a single sort button. The existing app bar continues to provide navigation and the file count; the Compose chrome must **not** echo its subtitle.
3. **Task-scoped commands:** creation is a single FAB menu for two related actions; selection and paste live in an elevated contextual dock above the scrollable content, not in a persistent row of every possible command.

The hierarchy is based on [P01], [P02], [V00] and [V01]. One accent hierarchy per state; no decorative animations, gradients, special shapes on every file, or faux hero banners for a utilitarian list.

## [C03, C10, C20, C27, C34] Components and gestures

| Need | Implementation | Reason |
| --- | --- | --- |
| Destination | Small legacy toolbar + concise Compose breadcrumb | Existing drawer/up navigation stays intact while migrating the app shell; current folder is visibly distinct |
| Search | Wide tonal search entry, in-place rounded `TextField` on focus, clear and close icons, proper IME Search, ViewModel-backed debouncing | Search results remain the same provider-aware list below, not a homegrown overlay or duplicated app-bar search; keep native text editing |
| List/grid | One 48dp tonal icon toggle with content description indicating the *next* view | Avoid two large mode chips permanently occupying content space |
| Sort/options | Native `ModalBottomSheet`, selectable grouped radio rows, separate rows with semantic `Switch` controls | Eight settings no longer pop over/hide a full screen of files; scrolling supports shorter windows and large type |
| List rows | Stable-key Compose lazy list with 2dp grouped seams, large outer/small inner corners, selected-state tonal distinction | [C10] differentiated grouping instead of one giant card per row |
| Grid tiles | Stable-key adaptive `LazyVerticalGrid`, restrained rounded media and two-line titles | No permanent Select buttons; no oversized square gray thumbnail backplates |
| Selection | Long-press or explicit file overflow **Select** (or **Deselect**); checkbox only once selection mode is active; dock with Copy, Cut, More, Clear | Reliable accessibility alternative to long press, without turning every file row into an editing form |
| Creation | Material 3 `FloatingActionButtonMenu` with file/folder labels, Back and outside dismissal | One predictable primary creation launcher, hidden during selection/search/paste and read-only/picker states |
| Paste | Contextual bottom dock with item count, action and cancel | No persistent toolbar duplication |
| Loading/errors | `LoadingIndicator` for initial short indeterminate waits, determinate file-job progress, explicit error recovery and empty state | Honest feedback; never equate a time-consuming file copy with a transient spinner |

All file actions continue to dispatch through the existing fragment/ViewModel/service/permission and archive code. Existing external picker intents and result grants are not reimplemented by these visual controls.

## Visual grammar and token ownership

- **Color:** `VFilesTheme` uses semantic Material `surface`/`surfaceContainer*`, `secondaryContainer/onSecondaryContainer`, `tertiaryContainer/onTertiaryContainer`; don't set grayscale icon background squares or ad-hoc RGB values. Material vectors have no View-theme `android:tint`; Compose owns their contrast.
- **Type:** `bodyLarge` for repeated file names, `bodySmall` for file metadata, one `titleSmallEmphasized` for location and `titleMediumEmphasized` for selection. Don't turn each file into a headline.
- **Shapes:** grouped list has softened first/last edges and narrow seams; media has modest corners; selected item uses a clearly different tonal container and increased shape; search is fully rounded.
- **Motion:** inherit `MaterialExpressiveTheme(MotionScheme.expressive())` and native surface/motion defaults; don't add a spring to each file row, every sort setting or keyboard focus. Reduced-motion and platform animation scale flow through `VFilesTheme`.
- **Spacing:** 16dp screen gutters, 8–12dp control/section rhythm, 48dp or larger accessible targets, extra bottom content padding so dock/FAB cannot trap the final row. Prefer reflow to clipped horizontal buttons.

## States and interaction contract

- **Idle:** search + path + entries visible immediately; no unselected checkboxes, no extra subtitle, no second sort entry in the legacy toolbar.
- **Search:** focus input and IME, clear query, submit results through original search flow, close/back restores normal browsing; visible query persists while focused.
- **Select:** item tint and checkbox appear; selection actions become a bottom dock; row click toggles selection until cleared; operations must still pass `FileBrowserCommandContext` write/read-only/archive policies. FAB hides.
- **Paste:** show real source count and destination write restrictions; cancel clears the source state. FAB hides.
- **Read-only/provider:** no create or destructive commands where disallowed; archive path and provider operations preserve existing service dispatch.
- **External picker/TV:** preserve existing RecyclerView/picker/keyboard alternatives rather than silently breaking URI modes or D-pad behavior.
- **Loading/empty/error:** specific recovery or truthful status. Keep scroll state per path and per mode; rerun sort on a background dispatcher, not during composition.

## [Q00] Verification matrix / known boundary

CI validates standard + systemPicker debug builds, JVM unit tests and compiles device UI tests. Compose UI tests cover the search field, icon-toggle semantics, sort-sheet state and contextual command dispatch. **Building device tests does not mean they ran.** Device visual/interaction checks are still required at 320/360/393/600/840/1200dp, 1.0/1.3/2.0 font scale, dynamic/light/dark/high contrast, RTL, IME/nav insets, TalkBack/keyboard, tiny windows and landscape. Test actual directory paths including local, remote, archive, permission-denied, many-thousand-item folders and pending transfers.

**Deliberate integration boundaries:** the legacy app-bar/drawer, external picker and TV flows remain View-hosted; `targetSdk` remains 34 pending integration-level Android 17 policy/testing. The Compose dependency stays on 1.5.0-alpha23 due to the app's minSdk 23 compatibility constraint (review against newer Material3/AndroidX release notes before changing). This screen should not be described as an entirely Compose-native Android 17 app shell until those boundaries are addressed.
