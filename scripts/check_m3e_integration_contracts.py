#!/usr/bin/env python3
"""Static integration guard for merged Settings + Secondary + Browser.

This checks source contracts, not Android runtime behaviour.
"""
from pathlib import Path

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

# Keep the original SAF picker, grants and URI result payloads while using Compose
# for ordinary non-TV browser sessions.
assert "viewModel.pickOptions == null" in browser and "isTelevision" in browser
assert "binding.breadcrumbLayout.isVisible = !compose" in browser
assert "R.id.browserChromeView).isVisible = compose" in browser
assert "R.id.browserCreateMenuView).isVisible = viewModel.pickOptions == null" in browser
assert "menuBinding.searchItem.isVisible = !useComposeFileList" in browser
assert "menuBinding.viewSortItem.isVisible = !searchViewExpanded && !useComposeFileList" in browser
assert "if (useComposeFileList)" in browser
assert "binding.fileJobProgressButton.isVisible = false" in browser
assert "viewModel.pickOptionsLiveData" in browser
assert "Intent.FLAG_GRANT_READ_URI_PERMISSION" in browser
assert "Intent.FLAG_GRANT_WRITE_URI_PERMISSION" in browser
assert "Intent.FLAG_GRANT_PREFIX_URI_PERMISSION" in browser
assert "extraPathList = paths.toList()" in browser
assert "browserContentView" in browser and "VFilesBrowserContent(" in browser
assert "VFilesBrowserChrome(" in browser and "VFilesCreateMenu(" in browser

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
