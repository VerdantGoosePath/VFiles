#!/usr/bin/env python3
"""Phase A static contract checks: no Android SDK or third-party Python packages needed.

These guards verify presence/shape of the old repository's critical public contracts.
They do NOT execute Android code, validate URI grants on a device or test UX.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
MAIN = ROOT / "app/src/main/AndroidManifest.xml"
PICKER = ROOT / "app/src/systemPicker/AndroidManifest.xml"
APP_GRADLE = ROOT / "app/build.gradle"
JAVA = ROOT / "app/src/main/java/me/zhanghai/android/files"


def attr(element, name):
    return element.get(ANDROID + name)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def activity_actions(component):
    return {attr(action, "name") for action in component.findall(".//intent-filter/action")}


def component(manifest, kind, suffix):
    matches = [
        element for element in manifest.findall(".//" + kind)
        if (attr(element, "name") or "").endswith(suffix)
    ]
    require(len(matches) == 1, f"Expected one {kind} ending with {suffix}")
    return matches[0]


def run():
    main = ET.parse(MAIN).getroot()
    picker = ET.parse(PICKER).getroot()
    gradle = APP_GRADLE.read_text(encoding="utf-8")

    require(re.search(r"\bminSdk\s+24\b", gradle), "minSdk 24 (required by material3 1.5.0-rc01) must be preserved")
    require("project.hasProperty('systemPicker')" in gradle, "Opt-in systemPicker flavor removed")

    main_permissions = {attr(p, "name") for p in main.findall("uses-permission")}
    picker_permissions = {attr(p, "name") for p in picker.findall("uses-permission")}
    require("android.permission.MANAGE_DOCUMENTS" not in main_permissions,
            "Standard flavor must not request signature MANAGE_DOCUMENTS")
    require(picker_permissions == {"android.permission.MANAGE_DOCUMENTS"},
            "systemPicker overlay must add only MANAGE_DOCUMENTS")

    for permission in (
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        "android.permission.INTERNET",
    ):
        require(permission in main_permissions, f"Missing baseline permission {permission}")

    main_activity = component(main, "activity", ".filelist.FileListActivity")
    require(attr(main_activity, "exported") == "true", "Main picker activity must be exported")
    required_actions = {
        "android.intent.action.MAIN",
        "android.intent.action.GET_CONTENT",
        "android.intent.action.OPEN_DOCUMENT",
        "android.intent.action.CREATE_DOCUMENT",
        "android.intent.action.OPEN_DOCUMENT_TREE",
    }
    require(required_actions <= activity_actions(main_activity),
            "Main activity lost a Storage Access Framework entry action")

    provider = component(main, "provider", ".file.FileProvider")
    require(attr(provider, "exported") == "false" and
            attr(provider, "grantUriPermissions") == "true",
            "Private FileProvider or its URI grants changed")
    documents = component(main, "provider", ".file.FileSystemDocumentsProvider")
    require(attr(documents, "exported") == "true" and
            attr(documents, "grantUriPermissions") == "true" and
            attr(documents, "permission") == "android.permission.MANAGE_DOCUMENTS",
            "DocumentsProvider export/grant/protection contract changed")
    require("android.content.action.DOCUMENTS_PROVIDER" in activity_actions(documents),
            "DocumentsProvider intent-filter changed")

    # File jobs perform dataSync; the FTP server hosts an incoming network connection
    # indefinitely and is now connectedDevice so it is not subject to a shared 6h dataSync cap.
    service_types = {
        ".filejob.FileJobService": "dataSync",
        ".ftpserver.FtpServerService": "connectedDevice",
    }
    for suffix, expected in service_types.items():
        service = component(main, "service", suffix)
        require(attr(service, "foregroundServiceType") == expected,
                f"Incorrect foreground service type for {suffix}: expected {expected}")
    for permission in (
        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
        "android.permission.CHANGE_NETWORK_STATE",
    ):
        require(permission in main_permissions,
                f"FTP connected-device service requires {permission}")

    for name in (
        "ArchiveFileSystemProvider.kt", "FtpFileSystemProvider.kt",
        "SftpFileSystemProvider.kt", "SmbFileSystemProvider.kt",
        "WebDavFileSystemProvider.kt", "RootFileSystemProvider.kt",
        "DocumentFileSystemProvider.kt", "FileJobs.kt",
        "ExternalStoragePickerUri.kt",
    ):
        require(any(JAVA.rglob(name)), f"Critical backend/contract source absent: {name}")

    print("Phase A static contracts: PASS (manifest, SDK baseline, picker and backend presence).")
    print("This is NOT an Android build, device, URI grant, or functional test.")


if __name__ == "__main__":
    try:
        run()
    except (AssertionError, ET.ParseError, OSError) as error:
        print(f"Phase A static contracts: FAIL: {error}", file=sys.stderr)
        sys.exit(1)
