# Android release verification

GitHub Actions builds the debug and unsigned release variants, runs the core and Android platform tests, runs release lint, and checks the merged **release** manifest. It has read-only repository permissions and does not need the signing key. A separate job renders and archives the Paparazzi previews every Monday and on manual workflow runs. These previews supplement tests on Android devices.

## Verify the signed APK locally

Use Python 3.11 or later, a JDK, and Android SDK Build Tools 35.0.0 or later. Build and sign the APK using the established Najwa release key, then run the following command with the path and version of the artifact being released.

```sh
python scripts/verify_release_apk.py ./Najwa-1.0.0.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" \
  --version-name 1.0.0 --version-code 101
```

The verifier checks the APK signature against the pinned Najwa certificate, checks ZIP alignment, audits the packaged manifest, checks its package and version, and prints the APK SHA-256. Save the digest alongside the release. After uploading the APK, download it and repeat the command with `--expected-sha256` set to the saved digest. This compares the downloaded bytes to the local signed artifact. The verification command does not require access to the signing key.

The release manifest can also be audited independently after building the unsigned variant.

```sh
./gradlew :app:assembleRelease
python scripts/audit_privacy.py --require-release-merged
```
