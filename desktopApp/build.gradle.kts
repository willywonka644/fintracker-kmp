import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Defined in gradle.properties so the Android and desktop builds cannot disagree.
val appVersion: String by project

// The About dialog used to carry a hand-written version string and sat two minor
// releases behind what was actually shipping. Generating the constant from the
// same property the packager uses makes that drift impossible.
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    val version = appVersion
    inputs.property("appVersion", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get()
            .file("io/github/willywonka644/fintracker/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.willywonka644.fintracker

            /** Generated from `appVersion` in gradle.properties. Do not edit by hand. */
            internal const val APP_VERSION: String = "$version"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(generateBuildInfo)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    // Ktor server (Wi-Fi sync)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core)

    // JmDNS (mDNS service registration so Android can discover the desktop)
    implementation(libs.jmdns)

    // SLF4J backend — required for KtorSyncServer logging to actually emit output
    implementation(libs.logback.classic)

    // Explicitly bundle the SQLite JDBC driver so the packager includes it in the distributable.
    // It is a transitive dep of :shared's desktopMain but the Compose packager only
    // reliably picks up deps declared directly on this module.
    implementation(libs.sqldelight.sqlite.driver)

    // ── Compose UI tests (Phase 6.5 Tier 2) ──────────────────────────────────
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    // Lets the two sync dialogs be driven against a scripted server instead of a
    // real Pi (#92). shared uses the same engine for its client tests.
    testImplementation(libs.ktor.client.mock)
}

// ui-test-junit4 provides JUnit4 rules, so the test task must run JUnit4 (not the
// JUnit Platform). This also makes kotlin("test") resolve to the kotlin-test-junit
// variant. UI tests need a real display — headless CI must wrap the run in xvfb.
tasks.withType<Test>().configureEach {
    useJUnit()

    // No `skiko.renderApi` here on purpose. SOFTWARE stood here briefly against
    // #103 and fixed nothing; the fault was a BoxWithConstraints redrawing without
    // end, not the renderer. Forcing software rendering only takes away whatever
    // acceleration the runner offers.

    // #103 left these two behind, and they earned their place. A Compose UI test
    // that never goes idle hangs forever, and a hung Gradle task is silent: one
    // run sat in CI for 41 minutes on a step that takes 1m22s, with nothing in the
    // log to say which test was stuck. `started` is the line that matters — the
    // last test announced before the silence is the one that hung.
    //
    // Deliberately not a JUnit `Timeout` rule: that runs each test on its own
    // thread, and Compose UI tests need the main one.
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
    // Imported at the top rather than written as java.time.Duration: inside a
    // Gradle Kotlin DSL project block, `java` resolves to the JavaPluginExtension
    // and shadows the package, so the qualified name does not compile.
    timeout.set(Duration.ofMinutes(15))
}

compose.desktop {
    application {
        mainClass = "io.github.willywonka644.fintracker.MainKt"
        jvmArgs("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "FinTracker Desktop"
            packageVersion = appVersion
            description = "Personal finance tracker"
            copyright = "© 2025 willywonka644"
            // java.sql is loaded reflectively by the SQLite JDBC driver and is
            // not detected by jlink's static analysis — must be declared explicitly.
            modules("java.sql", "java.naming", "java.security.jgss")
            windows {
                iconFile.set(project.file("src/main/resources/fintracker.ico"))
                menuGroup = "FinTracker"
                upgradeUuid = "4f8b2c3d-1a2e-4f3b-9c8d-5e6f7a8b9c0d"
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
