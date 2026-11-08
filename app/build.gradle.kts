plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose"); id("app.cash.paparazzi") version "1.3.5" apply false }
val renderPreview = providers.gradleProperty("previewSdk").orNull == "35"
val platformTests = providers.gradleProperty("platformTests").orNull == "true"
if (renderPreview) apply(plugin = "app.cash.paparazzi")
android {
    namespace = "org.najwa.app"
    // Paparazzi 1.x uses the Android 35 rendering bridge; APK builds stay on 36.
    compileSdk = if (providers.gradleProperty("previewSdk").orNull == "35") 35 else 36
    defaultConfig {
        applicationId = "org.najwa.app"
        minSdk = 26
        targetSdk = if (providers.gradleProperty("previewSdk").orNull == "35") 35 else 36
        versionCode = 101
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("official") {
            val path=System.getenv("NAJWA_KEYSTORE")
            if(!path.isNullOrBlank()) {
                storeFile=file(path)
                storePassword=System.getenv("NAJWA_STORE_PASSWORD")
                keyAlias=System.getenv("NAJWA_KEY_ALIAS")
                keyPassword=System.getenv("NAJWA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if(!System.getenv("NAJWA_KEYSTORE").isNullOrBlank())signingConfig=signingConfigs.getByName("official")
        }
    }
    if (renderPreview) sourceSets.getByName("test").java.srcDir("src/snapshotTest/kotlin")
    if (platformTests) {
        sourceSets.getByName("test").java.srcDir("src/platformTest/kotlin")
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

tasks.register("releaseDependencyInventory") {
    doLast {
        val target=rootProject.file("docs/release-review/runtime-dependencies.tsv")
        target.parentFile.mkdirs()
        val artifacts=configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        target.writeText("coordinate\tartifact\n"+artifacts.sortedBy {it.moduleVersion.id.toString()}.joinToString("\n") {"${it.moduleVersion.id}\t${it.file.name}"}+"\n")
    }
}
dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    if (platformTests) testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}

// Load instrumentation at JVM startup; dynamic agent attachment is unavailable
// in restricted Windows build environments.
if (renderPreview) tasks.withType<Test>().configureEach {
    doFirst {
        val agent = configurations.getByName("debugUnitTestRuntimeClasspath").files
            .first { it.name.startsWith("byte-buddy-agent-") && it.extension == "jar" }
        jvmArgs("-javaagent:${agent.absolutePath}")
    }
}
if (platformTests) tasks.withType<Test>().configureEach {
    val testHome = rootProject.layout.buildDirectory.dir("platform-test-home").get().asFile
    val testTemp = rootProject.file("../../../tmp/najwa-test")
    doFirst {testHome.mkdirs();testTemp.mkdirs()}
    systemProperty("user.home",testHome.absolutePath)
    systemProperty("java.io.tmpdir",testTemp.canonicalPath)
    systemProperty("robolectric.dependency.repo.url",System.getenv("NAJWA_ROBOLECTRIC_REPO") ?: "https://repo.maven.apache.org/maven2")
}
