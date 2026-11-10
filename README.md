<div align="center">

# نجوى · Najwa

**صلاتك أولاً، وضجيجك لاحقاً.**  
**Prayer first. Noise can wait.**

[تنزيل نجوى لأندرويد](https://github.com/taroq4-2/Najwa/releases/latest/download/Najwa.apk) · [الموقع العربي](https://taroq4-2.github.io/Najwa/) · [English website](https://taroq4-2.github.io/Najwa/en.html) · [الإصدارات · Releases](https://github.com/taroq4-2/Najwa/releases)

[![Android verification](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml/badge.svg)](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml) [![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-087A53)](LICENSE)

</div>

## مواقيت واضحة وهدوء تختاره · Prayer times and quiet on your terms

<div dir="rtl">

نجوى تطبيق أندرويد مفتوح المصدر يحسب مواقيت الصلاة على جهازك. اختر أذانك وتذكيرات الإقامة لكل صلاة، وتابع الموعد القادم من الويدجت. وإذا فعّلت الصمت التلقائي، يستعيد نجوى وضع الرنين السابق عند انتهاء المدة.

</div>

Najwa is an open-source Android app that calculates prayer times offline. Set adhan and iqama alerts per prayer and see the next prayer on your home screen. Optional automatic silent mode restores your previous ringer setting after the interval.

## معاينة التطبيق · App preview

| الرئيسية · Home | الويدجت · Widget | الأذان · Adhan alert |
| :---: | :---: | :---: |
| <img src="docs/assets/home.png" width="190" alt="Najwa prayer times and iqama countdown"> | <img src="docs/assets/widget.png" width="115" alt="Najwa home screen widget"> | <img src="docs/assets/notification.png" width="260" alt="Expanded adhan alert and iqama countdown"> |

**[من أول موعد إلى أول أذان](https://taroq4-2.github.io/Najwa/#setup)** · [Setup guide in English](https://taroq4-2.github.io/Najwa/en.html#setup)

## البناء والمساهمة · Build and contribute

لتشغيل الاختبارات استخدم JDK 21 وAndroid SDK 36. الشفرة متاحة للتعديل وفق [Apache 2.0](LICENSE).

Run the verification suite with JDK 21 and Android SDK 36. The source is available under the [Apache 2.0 license](LICENSE).

```sh
./gradlew -PplatformTests=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintRelease
```

Windows: use `gradlew.bat` with the same arguments. See [release verification](docs/release-verification.md) and [third-party notices](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.md). To suggest a change or report a problem, [open a GitHub issue](https://github.com/taroq4-2/Najwa/issues).

[os73.com](https://os73.com/) · [contact@os73.com](mailto:contact@os73.com)

اللهم ارحم ابي
