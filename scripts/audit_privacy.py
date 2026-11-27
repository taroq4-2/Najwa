import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = "{http://schemas.android.com/apk/res/android}"
APP_ID = "org.najwa.app"
ALLOWED_PERMISSIONS = {
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.POST_NOTIFICATIONS",
    # The built-in adhan plays from a media foreground service so it can be stopped from the notification.
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    # Tasbeeh and prayer-log taps, and the adhan notification pattern.
    "android.permission.VIBRATE",
    # Android 16+ live iqama countdown chip.
    "android.permission.POST_PROMOTED_NOTIFICATIONS",
    # Optional "use my location" city lookup: coarse only, asked at the moment of use, never sent anywhere.
    "android.permission.ACCESS_COARSE_LOCATION",
    f"{APP_ID}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}


def manifest_issues(manifest, release=False):
    permissions = {
        item.get(NS + "name", "") for item in manifest
        if item.tag.startswith("uses-permission")
    }
    issues = [f"Unexpected permission: {name}" for name in sorted(permissions - ALLOWED_PERMISSIONS)]
    app = manifest.find("application")
    if app is None:
        return issues + ["Application declaration missing"], permissions
    if app.get(NS + "allowBackup") != "false":
        issues.append("Backups must be disabled")
    if release and app.get(NS + "debuggable", "false") != "false":
        issues.append("Release must not be debuggable")
    for kind in ("activity", "activity-alias", "receiver", "service", "provider"):
        for item in app.findall(kind):
            name = item.get(NS + "name", "")
            exported = item.get(NS + "exported")
            # Explicit export state avoids relying on historical platform defaults.
            if exported not in {"true", "false"}:
                issues.append(f"Declare exported explicitly: {kind} {name}")
            if exported != "true":
                continue
            if kind == "activity" and name in {f"{APP_ID}.MainActivity", ".MainActivity"}:
                continue
            if (kind == "receiver" and name == "androidx.profileinstaller.ProfileInstallReceiver"
                    and item.get(NS + "permission") == "android.permission.DUMP"):
                continue
            # The quick-settings tile can only be bound by the system UI.
            if (kind == "service" and name in {f"{APP_ID}.platform.SilenceTileService", ".platform.SilenceTileService"}
                    and item.get(NS + "permission") == "android.permission.BIND_QUICK_SETTINGS_TILE"):
                continue
            issues.append(f"Review exported {kind}: {name}")
    # Each service is pinned to its one purpose: running prayer work, playing the adhan, or the silence tile.
    expected_services = {
        "PrayerExecutionService": lambda s: s.get(NS + "foregroundServiceType") in {"specialUse", "0x40000000"} and s.get(NS + "exported") == "false",
        "AdhanPlayer": lambda s: s.get(NS + "foregroundServiceType") in {"mediaPlayback", "0x2", "0x00000002"} and s.get(NS + "exported") == "false",
        "SilenceTileService": lambda s: s.get(NS + "foregroundServiceType") is None
            and s.get(NS + "permission") == "android.permission.BIND_QUICK_SETTINGS_TILE",
    }
    for service in app.findall("service"):
        name = service.get(NS + "name", "")
        short = name.removeprefix(f"{APP_ID}.platform.").removeprefix(".platform.")
        check = expected_services.get(short) if name != short else None
        if check is None or not check(service):
            issues.append(f"Unexpected background service: {name}")
    audio_provider = next((provider for provider in app.findall("provider")
        if provider.get(NS + "name") in {f"{APP_ID}.platform.AdhanFileProvider", ".platform.AdhanFileProvider"}), None)
    if (audio_provider is None or audio_provider.get(NS + "exported") != "false"
            or audio_provider.get(NS + "grantUriPermissions") != "true"):
        issues.append("Adhan audio provider must be private with per-URI grants")
    return issues, permissions


def source_issues():
    audio_paths = ROOT / "app/src/main/res/xml/adhan_audio_paths.xml"
    entries = list(ET.parse(audio_paths).getroot())
    issues = []
    # Only the imported adhan files and the share cache (timetables, QR images the person shares) may be exposed.
    shared = sorted((entry.tag, entry.get("path")) for entry in entries)
    if shared != [("cache-path", "shared/"), ("files-path", "adhan/")]:
        issues.append("Only the private adhan directory and the share cache may be shared")
    for file in (ROOT / "app/src/main/kotlin").rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        # Prayer schedules use setAlarmClock; volume and global DND remain untouched.
        for token in ("setStreamVolume(", "adjustStreamVolume(", "setInterruptionFilter(",
                      "setNotificationPolicy(", "HttpURLConnection", "FusedLocationProvider"):
            if token in content:
                issues.append(f"{file.relative_to(ROOT)}: {token}")
    return issues


def main():
    parser = argparse.ArgumentParser()
    requirement = parser.add_mutually_exclusive_group()
    requirement.add_argument("--require-merged", action="store_true")
    requirement.add_argument("--require-release-merged", action="store_true")
    args = parser.parse_args()
    merged_root = ROOT / "app/build/intermediates/merged_manifests"
    pattern = "release/**/AndroidManifest.xml" if args.require_release_merged else "**/AndroidManifest.xml"
    paths = sorted(merged_root.glob(pattern))
    merged = bool(paths)
    if args.require_release_merged and not merged:
        raise SystemExit("BLOCKED: release manifest unavailable; run :app:assembleRelease first.")
    if args.require_merged and not merged:
        raise SystemExit("BLOCKED: merged manifest unavailable; run :app:assembleDebug first.")
    if not paths:
        paths = [ROOT / "app/src/main/AndroidManifest.xml"]

    failures = source_issues()
    permissions = set()
    for path in paths:
        release = merged and path.relative_to(merged_root).parts[0] == "release"
        issues, declared_permissions = manifest_issues(ET.parse(path).getroot(), release=release)
        failures.extend(f"{path.relative_to(ROOT)}: {issue}" for issue in issues)
        permissions.update(declared_permissions)
    result = {
        "status": "FAIL" if failures else ("PASS" if merged else "SOURCE_ONLY_PASS"),
        "merged_manifest_checked": merged,
        "release_manifest_checked": merged and any(
            path.relative_to(merged_root).parts[0] == "release" for path in paths
        ),
        "permissions": sorted(permissions),
        "failures": failures,
    }
    print(json.dumps(result, indent=2))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
