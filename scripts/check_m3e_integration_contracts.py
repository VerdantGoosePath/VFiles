#!/usr/bin/env python3
"""Static integration guard for merged Settings + Secondary + Browser.

This checks source contracts, not Android runtime behaviour.
"""
from pathlib import Path
import re

root = Path("app/src/main")
java = root / "java/me/zhanghai/android/files"
manifest = (root / "AndroidManifest.xml").read_text()
browser = (java / "filelist/FileListFragment.kt").read_text()
jobs = (java / "filejob/FileJobs.kt").read_text()
intent = (java / "util/IntentExtensions.kt").read_text()
theme = (java / "compose/theme/VFilesTheme.kt").read_text()
settings = (java / "settings/ExpressiveSettingsScreen.kt").read_text()
gradle = Path("app/build.gradle").read_text()

assert "MaterialExpressiveTheme" in theme and "MotionScheme.expressive()" in theme
assert "Settings.MATERIAL_DESIGN_3.valueCompat" not in (java / "theme/custom/CustomThemeHelper.kt").read_text()
assert not (root / "res/xml/settings.xml").exists()
assert "MATERIAL_DESIGN_3" not in settings
for removed in ("ImageViewerActivity", "TextEditorActivity", "maybeAddImageViewerActivityExtras"):
    assert removed not in browser and removed not in manifest, removed
for package in ("viewer/image", "viewer/text"):
    assert not (java / package).exists(), package
assert "viewer.saveas.SaveAsActivity" in manifest
assert "FileListActivity" in manifest and "FileSystemDocumentsProvider" in manifest

# Keep the original SAF picker grants and URI result payloads; every non-TV session,
# external picker included, uses the Compose browser.
assert "viewModel.pickOptions == null" in browser and "isTelevision" in browser
assert "get() = isAdded && !requireContext().isTelevision\n" in browser
assert "override fun onPickCurrentDirectory()" in browser and "override fun onPickCreateFile(" in browser
# The touch browser is one full-screen Compose surface; the View tree stays for TV/pickers.
assert "legacyBrowserView.isVisible = !compose" in browser
assert "browserScreenView?.isVisible = compose" in browser
assert re.search(r"R\.id\.browserCreateMenuView\)\.isVisible =\s+viewModel\.pickOptions == null", browser)
assert "menuBinding.searchItem.isVisible = !useComposeFileList" in browser
assert "menuBinding.viewSortItem.isVisible = !searchViewExpanded && !useComposeFileList" in browser
assert "if (useComposeFileList)" in browser
assert "binding.fileJobProgressButton.isVisible = false" in browser
assert "viewModel.pickOptionsLiveData" in browser
assert "Intent.FLAG_GRANT_READ_URI_PERMISSION" in browser
assert "Intent.FLAG_GRANT_WRITE_URI_PERMISSION" in browser
assert "Intent.FLAG_GRANT_PREFIX_URI_PERMISSION" in browser
assert "extraPathList = paths.toList()" in browser
screen = (java / "filelist/FileBrowserScreen.kt").read_text()
assert "FileBrowserScreen(" in browser and "VFilesCreateMenu(" in browser
assert "VFilesBrowserContent(" in screen and "BrowserHeader(" in screen
assert "ExpandedFullScreenContainedSearchBar(" in screen
# Compact windows use an in-window modal rail so a swipe can pull it out under the finger.
assert "ModalBrowserRail(" in screen and "WideNavigationRail(" in screen
assert "BrowserPickerBar(chrome, actions)" in screen
assert "NavigationDrawer" not in screen

# ACTION_VIEW must remain external with no internal-viewer private path extras.
# The original Open-with affordance retains Edit and Open-as alternatives.
opener = browser.split("    private fun openFileWithIntent(", 1)[1].split(
    "    // If the user picked a default app", 1
)[0]
assert "fileProviderUri.createViewIntent(mimeType)" in opener
assert "extraPath =" not in opener
assert "applyDefaultAppPackage(this, mimeType)" in opener
assert "EditFileActivity::class.createIntent()" in opener
assert "OpenFileAsDialogActivity::class.createIntent()" in opener
assert "it.withChooser(" in opener
assert "fileProviderUri.createEditIntent" in browser
assert "FLAG_GRANT_READ_URI_PERMISSION" in intent
assert "FLAG_GRANT_WRITE_URI_PERMISSION" in intent

assert "applyExternalDefaultOpenApp(mimeType, service)" in jobs
assert "it.withChooser(" in jobs
assert ".apply { extraPath = file }" not in jobs
assert "com.github.chrisbanes:PhotoView" not in gradle
assert "subsampling-scale-image-view-androidx" not in gradle
print("M3E integrated static contracts: PASS (not a device smoke test).")
