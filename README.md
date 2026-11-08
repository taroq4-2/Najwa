# Najwa · نجوى

**صلاتك أولاً، وضجيجك لاحقاً.**  
**Prayer first. Noise can wait.**

نجوى تطبيق أندرويد مفتوح المصدر لمواقيت الصلاة والتنبيهات والصمت التلقائي.  
Najwa is an open-source Android app for prayer times, reminders and automatic silent mode.

| العربية | English |
| --- | --- |
| [صفحة التطبيق](https://taroq4-2.github.io/Najwa/) | [App website](https://taroq4-2.github.io/Najwa/en.html) |
| [تحميل APK الرسمي](https://github.com/taroq4-2/Najwa/releases/download/v1.0.0/Najwa-1.0.0.apk) | [Download the signed APK](https://github.com/taroq4-2/Najwa/releases/download/v1.0.0/Najwa-1.0.0.apk) |

[![Android verification](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml/badge.svg)](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml)

## البناء والتعديل · Build and contribute

يتطلب البناء JDK 17 وAndroid SDK 36. الشفرة متاحة للجميع للتعديل وفق [Apache 2.0](LICENSE).  
Build with JDK 17 and Android SDK 36. Anyone can modify the source under the [Apache 2.0 license](LICENSE).

```sh
./gradlew -PplatformTests=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintRelease
```

Windows: use `gradlew.bat` with the same arguments.  
Data and font attributions are in [Third-party notices](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.md).

[os73.com](https://os73.com/) · [contact@os73.com](mailto:contact@os73.com)

اللهم ارحم ابي
