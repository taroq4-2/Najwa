plugins { kotlin("jvm") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.withType<JavaCompile>().configureEach { sourceCompatibility = "17"; targetCompatibility = "17" }
dependencies { testImplementation("junit:junit:4.13.2") }
// Forward -Pnajwa.golden=<file> to the opt-in GoldenExportTest (iOS reference times).
tasks.withType<Test>().configureEach { providers.gradleProperty("najwa.golden").orNull?.let { systemProperty("najwa.golden", it) } }
