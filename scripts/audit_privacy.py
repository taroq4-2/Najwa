import argparse, json, re
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--require-merged',action='store_true');args=p.parse_args()
root=Path(__file__).resolve().parents[1]
paths=list((root/'app/build/intermediates').glob('merged_manifests/**/AndroidManifest.xml'))
merged=bool(paths)
if not paths: paths=[root/'app/src/main/AndroidManifest.xml']
allowed={'android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_SPECIAL_USE','android.permission.MODIFY_AUDIO_SETTINGS','android.permission.ACCESS_NOTIFICATION_POLICY','android.permission.SCHEDULE_EXACT_ALARM','android.permission.RECEIVE_BOOT_COMPLETED','android.permission.POST_NOTIFICATIONS','org.najwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
ns='{http://schemas.android.com/apk/res/android}'
failures=[];permissions=set()
for path in paths:
    tree=ET.parse(path)
    for item in tree.findall('uses-permission'):
        name=item.get(ns+'name');permissions.add(name)
        if name not in allowed: failures.append('Unexpected permission: '+str(name))
    app=tree.find('application')
    if app.get(ns+'allowBackup')!='false': failures.append('Backups must be disabled')
    for kind in ('receiver','service','provider'):
        for item in app.findall(kind):
            if item.get(ns+'exported')=='true':
                # AndroidX's tooling receiver is restricted to the privileged DUMP
                # permission. Never allow the same receiver without that guard.
                reviewed = (kind == 'receiver'
                    and item.get(ns+'name') == 'androidx.profileinstaller.ProfileInstallReceiver'
                    and item.get(ns+'permission') == 'android.permission.DUMP')
                if not reviewed: failures.append('Review exported '+kind+': '+str(item.get(ns+'name')))
    for service in app.findall('service'):
        if service.get(ns+'name') not in ('org.najwa.app.platform.PrayerExecutionService','.platform.PrayerExecutionService') or service.get(ns+'foregroundServiceType')!='specialUse' or service.get(ns+'exported')!='false':
            failures.append('Unexpected background service')
    audio_provider=app.find("provider[@"+ns+"name='org.najwa.app.platform.AdhanFileProvider']")
    if audio_provider is not None and (audio_provider.get(ns+'exported')!='false' or audio_provider.get(ns+'grantUriPermissions')!='true'):
        failures.append('Adhan audio provider must be private with per-URI grants')
audio_paths=root/'app/src/main/res/xml/adhan_audio_paths.xml'
if audio_paths.exists():
    entries=list(ET.parse(audio_paths).getroot())
    if len(entries)!=1 or entries[0].tag!='files-path' or entries[0].get('path')!='adhan/':
        failures.append('Only the private adhan directory may be shared')
for file in (root/'app/src/main/kotlin').rglob('*.kt'):
    content=file.read_text(encoding='utf-8')
    # Prayer schedules use setAlarmClock; volume and global DND remain untouched.
    for token in ('setStreamVolume(', 'adjustStreamVolume(', 'setInterruptionFilter(', 'setNotificationPolicy(', 'HttpURLConnection', 'FusedLocationProvider'):
        if token in content: failures.append(str(file.relative_to(root))+': '+token)
result={'status':'FAIL' if failures else ('PASS' if merged else 'SOURCE_ONLY_PASS'),'merged_manifest_checked':merged,'permissions':sorted(permissions),'failures':failures}
print(json.dumps(result,indent=2))
if failures: raise SystemExit(1)
if args.require_merged and not merged: raise SystemExit('BLOCKED: merged manifest unavailable; run assembleDebug first.')
