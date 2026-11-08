# Najwa third-party notices

This notice covers the 53 external artifacts in the resolved application runtime graph reviewed on 2026-11-08, plus the two packaged Noto Sans Arabic fonts. Dependencies may be reduced by the release code shrinker; this notice deliberately retains their attributions. The internal `Najwa:core` module is application code and is not a third-party dependency.

License texts are packaged with the application in `assets/licenses/`: `Apache-2.0.txt`, `Boost-1.0.txt`, and `Noto-OFL.txt`. This notice is included there as `THIRD_PARTY_NOTICES.md`. These third-party licenses do not assign a license to Najwa's own source code, logo, or other original materials.

## AndroidX, Jetpack Compose, and Material components

The Android Open Source Project and the respective component contributors.

License: Apache License 2.0. Exact artifact versions appear in the inventory below. License declarations were checked against the cached published POMs; the complete Apache terms were copied verbatim from `androidx.core:core:1.15.0`, entry `META-INF/androidx/core/core/LICENSE.txt`. The identical text also occurs in `core-ktx:1.15.0`.

The graphics-path native source includes Android Open Source Project path/math code and Filament math headers under Apache-2.0. Credits include Copyright 2022 The Android Open Source Project; Copyright (C) 2006 The Android Open Source Project; and Copyright 2013 The Android Open Source Project. Its Android/Skia platform interaction does not by itself mean the complete Skia engine is distributed inside this application.

Source: https://github.com/androidx/androidx

## Kotlin standard library 2.0.21

Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.

License: Apache License 2.0. The upstream attribution inventory identifies these embedded JVM/common contributions:

- Collection code derived from GWT, Copyright 2007-08 Google Inc. (Apache-2.0).
- Unsigned JVM utilities derived from Guava's UnsignedLongs, Copyright 2011 The Guava Authors (Apache-2.0).
- JVM mathematical functions derived from Boost special math functions, Copyright Eric Ford & Hubert Holin 2001 (Boost Software License 1.0, included as `Boost-1.0.txt`).

Sources: https://github.com/JetBrains/kotlin/blob/v2.0.21/license/COPYRIGHT.txt and https://github.com/JetBrains/kotlin/blob/v2.0.21/license/README.md

## kotlinx.coroutines 1.9.0

Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.

License: Apache License 2.0. This is the copyright wording carried in the upstream 1.9.0 license; its dates have not been altered to match the app release date.

Source: https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/LICENSE.txt

## JetBrains Java annotations 23.0.0

JetBrains s.r.o. and contributors. License: Apache License 2.0, as declared by the published `org.jetbrains:annotations:23.0.0` POM.

Source: https://github.com/JetBrains/java-annotations

## Guava ListenableFuture 1.0

Copyright (C) 2007 The Guava Authors.

License: Apache License 2.0. The artifact inherits its license from `com.google.guava:guava-parent:26.0-android`; the complete Guava library is not an additional runtime artifact in this graph.

Source: https://github.com/google/guava/blob/v26.0/guava/src/com/google/common/util/concurrent/ListenableFuture.java

## Noto Sans Arabic, version 2.010

Copyright 2022 The Noto Project Authors (https://github.com/notofonts/arabic)

License: SIL Open Font License 1.1. The full copyright and license are in `Noto-OFL.txt`. The regular and bold TTF name tables both declare this copyright, version 2.010, and OFL 1.1. Font files: `noto_sans_arabic_regular.ttf` and `noto_sans_arabic_bold.ttf`.

## GeoNames

The offline places catalog is derived from GeoNames country dumps, under Creative Commons Attribution 4.0. Najwa filters populated places, derives display names and search aliases, and sorts by population. Data refreshed 2026-11-08.

Source: https://www.geonames.org/export/ · https://creativecommons.org/licenses/by/4.0/

## Resolved external runtime artifacts

All coordinates below declare Apache-2.0 directly or through the Guava parent POM. Kotlin's embedded Boost attribution is additionally covered above.

| Coordinate | License |
| --- | --- |
| `androidx.activity:activity-compose:1.9.3` | Apache-2.0 |
| `androidx.activity:activity-ktx:1.9.3` | Apache-2.0 |
| `androidx.activity:activity:1.9.3` | Apache-2.0 |
| `androidx.annotation:annotation-experimental:1.4.1` | Apache-2.0 |
| `androidx.annotation:annotation-jvm:1.8.1` | Apache-2.0 |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 |
| `androidx.collection:collection-jvm:1.4.4` | Apache-2.0 |
| `androidx.collection:collection-ktx:1.4.4` | Apache-2.0 |
| `androidx.compose.animation:animation-android:1.7.5` | Apache-2.0 |
| `androidx.compose.animation:animation-core-android:1.7.5` | Apache-2.0 |
| `androidx.compose.foundation:foundation-android:1.7.5` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android:1.7.5` | Apache-2.0 |
| `androidx.compose.material3:material3-android:1.3.1` | Apache-2.0 |
| `androidx.compose.material:material-icons-core-android:1.7.5` | Apache-2.0 |
| `androidx.compose.material:material-ripple-android:1.7.5` | Apache-2.0 |
| `androidx.compose.runtime:runtime-android:1.7.5` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-text-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android:1.7.5` | Apache-2.0 |
| `androidx.compose.ui:ui-util-android:1.7.5` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 |
| `androidx.core:core-ktx:1.15.0` | Apache-2.0 |
| `androidx.core:core:1.15.0` | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 |
| `androidx.emoji2:emoji2:1.3.0` | Apache-2.0 |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.3` | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller:1.3.1` | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx:1.2.1` | Apache-2.0 |
| `androidx.savedstate:savedstate:1.2.1` | Apache-2.0 |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.0.21` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0` | Apache-2.0 |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 |

## Scope and maintenance

The recorded resolution includes one duplicate `androidx.core:core:1.15.0` row; it is listed once here. JUnit, Hamcrest, Android instrumented-test dependencies, Gradle, the Android Gradle plugin, and the Kotlin compiler are development/test tooling, not application runtime components in this inventory. If distributing those binaries separately, preserve their own accompanying licenses and notices.

The original application source license is not established by the recovered project. No source-code license is granted by this notice. Before publishing Najwa's source under an open-source license, its owner must select that license explicitly.

Audit evidence, artifact hashes, published POM declarations, font metadata, and archive notice paths are in `docs/release-review/license-scan.json`; the source dependency list is `docs/release-review/runtime-dependencies.tsv`. Regenerate and review this inventory whenever runtime dependencies or bundled media change. Retain these notice assets in both the Google Play bundle and the website APK.
