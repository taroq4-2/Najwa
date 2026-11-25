# Baseline for the worship companion work

Recorded before any worship-engine change, on branch `worship-engine`.

| Item | Value |
| :--- | :--- |
| Base commit | `92abace` (android-1.4, Najwa 1.6.0, versionCode 117) |
| Modules | `:core` (pure Kotlin, JUnit) and `:app` (Android, Compose, RemoteViews widgets) |
| SDK | minSdk 26, targetSdk 36 |
| Release APK (unsigned, zipaligned, R8) | 19,283,591 bytes |
| Core tests | 119 passed, 0 failed |
| App tests (Robolectric, `-PplatformTests=true`) | 119 passed, 0 failed |
| Release lint | 0 errors, 154 warnings |
| Privacy audit (`scripts/audit_privacy.py --require-release-merged`) | PASS |
| Runtime dependencies (release classpath, OSV scan) | 56, no advisories |

## Merged release manifest permissions

- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_NOTIFICATION_POLICY`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.POST_PROMOTED_NOTIFICATIONS`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.SCHEDULE_EXACT_ALARM`
- `android.permission.VIBRATE`
- `org.najwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

## Existing persistence used by the new features

- Prayer log: `files/prayer-log.json` (`PrayerLogStore`, statuses per prayer per day).
- Adhkar: SharedPreferences `adhkar` (today's remaining counts per category, completed days).
- Tasbeeh: SharedPreferences `tasbeeh` (shared with the widget).
- Quran: SharedPreferences `quran` (current page, goal, khatmas, pages per day). The app has no Quran text.

## Widgets

PrayerWidget (seven size layouts: large, medium, wide, tall, slim, compact, mini, plus tiny), PrayerListWidget, MinimalWidget, PrayerLogWidget, TasbeehWidget.
