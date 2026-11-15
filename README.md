<div align="center">

# نجوى · Najwa

![نجوى — عرض التطبيق · Najwa product presentation](docs/assets/najwa-share-v122.jpg)

**صلاتك أولاً، وضجيجك لاحقاً.**  
**Prayer first. Noise can wait.**

**1.2.2 · Build 113** — Android 8.0+ · Offline · 7 languages · Apache 2.0

[تنزيل نجوى لأندرويد](https://github.com/taroq4-2/Najwa/releases/latest/download/Najwa.apk) · [الموقع العربي](https://taroq4-2.github.io/Najwa/) · [English website](https://taroq4-2.github.io/Najwa/en.html) · [الإصدارات · Releases](https://github.com/taroq4-2/Najwa/releases)

[![Android verification](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml/badge.svg)](https://github.com/taroq4-2/Najwa/actions/workflows/android.yml) [![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-087A53)](LICENSE)

</div>

## مواقيت واضحة وهدوء تختاره · Prayer times and quiet on your terms

<div dir="rtl">

نجوى تطبيق أندرويد مفتوح المصدر يحسب مواقيت الصلاة على جهازك. اختر أذانك وتذكيرات الإقامة لكل صلاة، وتابع الموعد القادم من الويدجت. وإذا فعّلت الصمت التلقائي، يستعيد نجوى وضع الرنين السابق عند انتهاء المدة.

</div>

Najwa is an open-source Android app that calculates prayer times offline. Set adhan and iqama alerts per prayer and see the next prayer on your home screen. Optional automatic silent mode restores your previous ringer setting after the interval.

<div dir="rtl">

هوية **مدار السكينة** تجمع الألوان الزمردية والعاجية مع واجهة واضحة وويدجت متجاوب. ابحث في أكثر من 459 ألف مكان في 246 دولة وإقليم. تتوفر العربية والإنجليزية والإندونيسية والأردية والبنغالية والهندية والتركية.

يبدأ الصمت الاختياري بعد الأذان بـ20 ثانية، وتُخصّص مدة الإقامة والصمت لكل صلاة. عند الإقامة ينتقل العداد للموعد التالي. مواقيت الإقامة تتبع إعدادك؛ راجع مسجدك للمواعيد الفعلية.

</div>

The **Madar** design pairs emerald and ivory with a clear interface and responsive widgets. Search over 459,000 places across 246 countries and territories. Arabic, English, Indonesian, Urdu, Bengali, Hindi and Turkish are supported. Optional silence starts 20 seconds after adhan; iqama and silence durations are configurable for each prayer.

## معاينة التطبيق · App preview

| الرئيسية الداكنة · Dark | الرئيسية الفاتحة · Light | English |
| :---: | :---: | :---: |
| <img src="docs/assets/home.png" width="220" alt="واجهة نجوى الداكنة"> | <img src="docs/assets/home-light.png" width="220" alt="واجهة نجوى الفاتحة"> | <img src="docs/assets/home-en.png" width="220" alt="Najwa English home screen"> |

### الويدجت · Widgets

![ويدجت نجوى العريض والطولي](docs/assets/najwa-widgets-studio.png)

### الأذان والإقامة · Adhan & iqama

![إشعارات نجوى للأذان والإقامة](docs/assets/najwa-notifications-studio.png)

**[من أول موعد إلى أول أذان](https://taroq4-2.github.io/Najwa/#setup)** · [Setup guide in English](https://taroq4-2.github.io/Najwa/en.html#setup)

## الإصدار والتحقق · Release verification

ملف APK موقّع بالمفتاح الرسمي لدعم التحديث مع الحفاظ على بياناتك. نجح 170 اختبارًا محليًا وفحوص GitHub، وفُحصت تحسينات الإشعارات على Samsung S23 Ultra بنظام Android 17. [تفاصيل الإصدار](https://github.com/taroq4-2/Najwa/releases/tag/v1.2.2).

The APK retains the original signing key for seamless updates. All 170 local core and platform tests and GitHub verification passed; notification improvements were exercised on a Samsung S23 Ultra running Android 17. [Release details](https://github.com/taroq4-2/Najwa/releases/tag/v1.2.2).

SHA-256 · `Najwa.apk` · 1.2.2 / 113:

```text
a737b4778128cd6dc58c99d21b972fa650691f156527e59e3d42b04a97488136
```

## البناء والمساهمة · Build and contribute

لتشغيل الاختبارات استخدم JDK 21 وAndroid SDK 36. الشفرة متاحة للتعديل وفق [Apache 2.0](LICENSE).

Run the verification suite with JDK 21 and Android SDK 36. The source is available under the [Apache 2.0 license](LICENSE).

```sh
./gradlew -PplatformTests=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintRelease
```

Windows: use `gradlew.bat` with the same arguments. See [release verification](docs/release-verification.md) and [third-party notices](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.md). To suggest a change or report a problem, [open a GitHub issue](https://github.com/taroq4-2/Najwa/issues).

[os73.com](https://os73.com/) · [contact@os73.com](mailto:contact@os73.com)

اللهم ارحم ابي
