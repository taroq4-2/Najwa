# Android release verification

GitHub Actions builds the debug and unsigned release variants, runs the core and Android platform tests, runs release lint, and checks the merged **release** manifest. It has read-only repository permissions and does not need the signing key. A separate job renders and archives the Paparazzi previews every Monday and on manual workflow runs. These previews supplement tests on Android devices.

## Verify the signed APK locally

Use Python 3.11 or later, a JDK, and Android SDK Build Tools 35.0.0 or later. Build and sign the APK using the established Najwa release key, then run the following command with the path and version of the artifact being released.

```sh
python scripts/verify_release_apk.py "./Najwa-$VERSION_NAME.apk" \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" \
  --version-name "$VERSION_NAME" --version-code "$VERSION_CODE"
```

The verifier checks the APK signature against the pinned Najwa certificate, checks ZIP alignment, audits the packaged manifest, checks its package and version, and prints the APK SHA-256. Save the digest alongside the release. After uploading the APK, download it and repeat the command with `--expected-sha256` set to the saved digest. This compares the downloaded bytes to the local signed artifact. The verification command does not require access to the signing key.

The manual **Verify distributed APK** workflow repeats these checks against the release download. Run **Android verification** manually to exercise the emulator matrix and preview captures. The runtime inventory job produces a CycloneDX document and looks up the resolved Maven versions in OSV. Dependency review runs for pull requests.

## Device acceptance

- On Android 17 and on a second manufacturer's device, exercise adhan, silence, iqama, and restoration with the screen locked and in Doze. Repeat with exact alarms revoked during silence, policy access revoked and regranted, a manual ringer change, and a device restart.
- Import MP3 and M4A, delete the source, restart, and listen for the scheduled adhan. Confirm that changing language preserves custom sound and channel preferences.
- Check the settings and widget at 200% font size and with TalkBack in both languages. Verify resize and color behavior in the launchers used by testers.
- Measure cold start using `adb shell am start -W` after a force stop on a dedicated test device. Use Perfetto to compare city search, frame timing, and broadcast processing. Compare `dumpsys alarm`, `dumpsys meminfo org.najwa.app`, and battery data on the same device and schedule over 24–72 hours.
- The offline core suite compares representative calculated times with [AlAdhan](https://aladhan.com/prayer-times-api) using the same coordinates and methods, including London on its DST transition date. It does not validate a mosque's iqama schedule.

The release manifest can also be audited independently after building the unsigned variant.

```sh
./gradlew :app:assembleRelease
python scripts/audit_privacy.py --require-release-merged
```
