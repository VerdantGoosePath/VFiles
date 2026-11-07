#!/usr/bin/env python3
"""Phase F static safety/regression checks. These do NOT substitute for device QA."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path("app/src/main")
src = root / "java/me/zhanghai/android/files"
android = "{http://schemas.android.com/apk/res/android}"
manifest_source = (root / "AndroidManifest.xml").read_text()
manifest = ET.fromstring(manifest_source)
assert 'android:theme="@style/Theme.MaterialFiles"' not in manifest_source
assert 'android:theme="@style/Theme.MaterialFiles.Translucent"' not in manifest_source
application = manifest.find("application")
assert application is not None
assert application.get(android + "enableOnBackInvokedCallback") == "true"
assert application.get(android + "resizeableActivity") == "true"
assert application.get(android + "supportsRtl") == "true"
activities = application.findall("activity")
providers = application.findall("provider")
services = application.findall("service")
for activity in activities:
    if activity.find("intent-filter") is not None:
        assert activity.get(android + "exported") is not None, activity.get(android + "name")
provider = lambda suffix: next(
    p for p in providers if (p.get(android + "name") or "").endswith(suffix)
)
files = provider(".FileProvider")
documents = provider(".FileSystemDocumentsProvider")
assert files.get(android + "exported") == "false"
assert files.get(android + "grantUriPermissions") == "true"
assert documents.get(android + "exported") == "true"
assert documents.get(android + "permission") == "android.permission.MANAGE_DOCUMENTS"
assert documents.get(android + "grantUriPermissions") == "true"
assert all(p.get(android + "authorities") for p in providers)
assert any((s.get(android + "name") or "").endswith(".FileJobService")
           and s.get(android + "foregroundServiceType") == "dataSync" for s in services)
assert any((s.get(android + "name") or "").endswith(".FtpServerService")
           and s.get(android + "foregroundServiceType") == "connectedDevice" for s in services)

build = Path("app/build.gradle").read_text()
assert re.search(r"compileSdk\s*=\s*37\b", build)
assert re.search(r"minSdk\s+23\b", build)
# Android17 targeting is an explicit canary while runtime behaviour remains unverified.
assert "targetSdk (project.hasProperty('targetApi37') ? 37 : 34)" in build
for dependency in ("io.coil-kt:coil", "io.coil-kt:coil-video",
                   "androidx.exifinterface:exifinterface",
                   "me.zhanghai.android.libarchive:library"):
    assert dependency in build

theme = (src / "compose/theme/VFilesTheme.kt").read_text()
assert "MaterialExpressiveTheme(" in theme and "MotionScheme.expressive()" in theme
assert "baseThemeName.startsWith(material3ThemeName)" in (src / "theme/custom/CustomThemeHelper.kt").read_text()
assert "headlineMediumEmphasized" in (src / "about/AboutFragment.kt").read_text()
browser = (src / "filelist/FileBrowserContent.kt").read_text()
assert "key(state.pathKey, state.files" in browser
assert "produceState<List<FileItem>?>(initialValue = null)" in browser
assert "visibleResult == null" in browser
fragment = (src / "filelist/FileListFragment.kt").read_text()
assert "getSubtitle(files.orEmpty())" in fragment
assert "throwable.printStackTrace()" not in fragment
assert "Modifier.clearAndSetSemantics { }" in browser
assert "measuredDockHeight" in browser and "onSizeChanged" in browser
chrome = (src / "filelist/FileBrowserChrome.kt").read_text()
assert "modifier = Modifier.clearAndSetSemantics { }" in chrome
assert "Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())" in chrome
scaffold = (src / "compose/components/SurfaceComponents.kt").read_text()
assert "IconButton(onClick = onBack)" in scaffold
assert "settings_expressive_back_24dp" in scaffold

ftp = (src / "ftpserver/ExpressiveFtpServerScreen.kt").read_text()
assert "parseFtpListeningPort" in ftp and "it in 1..65535" in ftp
assert "rememberSaveable(title)" in ftp and "if (secret) remember(title)" in ftp
assert (Path("app/src/test/java/me/zhanghai/android/files/ftpserver/FtpPortValidationTest.kt")).exists()
save_as = (src / "viewer/saveas/SaveAsActivity.kt").read_text()
assert "if (savedInstanceState != null) return" in save_as
opener = (src / "filelist/OpenFileActivity.kt").read_text()
assert "fileProviderUri.createViewIntent" in opener
assert "applyExternalDefaultOpenApp" in opener
assert "FileJobService.open" in opener
assert not (src / "viewer/image").exists()
assert not (src / "viewer/text").exists()
assert "viewer.saveas.SaveAsActivity" in (root / "AndroidManifest.xml").read_text()
job_service = (src / "filejob/FileJobService.kt").read_text()
assert "try {\n                    job.runOn(this)\n                } finally {" in job_service
assert "runningJobs.remove(job)" in job_service
job_base = (src / "filejob/FileJob.kt").read_text()
assert "safeRemoteErrorMessage(e, emptyList())" in job_base
assert "service.showToast(e.toString())" not in job_base
picker = (root.parent / "systemPicker/AndroidManifest.xml").read_text()
assert "android.permission.MANAGE_DOCUMENTS" in picker
assert "android.permission.MANAGE_DOCUMENTS" not in (root / "AndroidManifest.xml").read_text().split("<application", 1)[0]
release = Path(".github/workflows/android.yml").read_text()
assert 'platforms;android-37.0' in release and 'platforms;android-36' not in release
assert 'minTouchTargetSize">0dp' not in (root / "res/values/themes_material3.xml").read_text()
assert "systemContrast(context)" in theme
assert "tintDestructivePositiveButton()" in (src / "filelist/ConfirmDeleteFilesDialogFragment.kt").read_text()
assert "tintDestructivePositiveButton()" in (src / "filelist/ConfirmReplaceFileDialogFragment.kt").read_text()
assert "itemBinding.text.isFocusable = true" in (src / "fileproperties/FilePropertiesTabFragment.kt").read_text()
assert 'android:foregroundServiceType="connectedDevice"' in manifest_source
assert "FOREGROUND_SERVICE_CONNECTED_DEVICE" in manifest_source
assert "CHANGE_NETWORK_STATE" in manifest_source
assert "override fun onTimeout(startId: Int, fgsType: Int)" in (src / "filejob/FileJobService.kt").read_text()
assert "START_NOT_STICKY" in (src / "filejob/FileJobService.kt").read_text()
print(f"Phase F source contracts: PASS ({len(activities)} activities, {len(providers)} providers, {len(services)} services); not a device test.")
