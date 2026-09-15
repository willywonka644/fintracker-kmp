plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    @Suppress("DEPRECATION")
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    jvm("desktop")

    // Android and desktop are both JVM targets, so the actuals for crypto and money
    // formatting used to be two byte-identical copies with nothing to notice if they
    // ever drifted apart (#102). One source set for both instead: src/jvmSharedMain.
    // commonTest runs against the desktop target only, so this is also what makes
    // those tests cover the code the Android app actually ships.
    //
    // Extending the default template rather than wiring dependsOn() by hand on
    // purpose: a manual edge switches the default hierarchy off, and with it the
    // commonTest -> desktopTest edge that carries every shared test.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            // The sync client (#91). Here rather than in app because the desktop
            // needs the identical client in #92, and the same Ktor version the
            // server already uses, so the two halves cannot drift apart.
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Lets the error mapping — 401 vs unreachable vs timeout — be pinned
            // in commonTest instead of only tried out against a real Pi.
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                // Same engine as Android on purpose: one HTTP stack means one set
                // of timeout and failure behaviours to reason about, not two.
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

android {
    namespace = "io.github.willywonka644.fintracker.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("FintrackerDatabase") {
            packageName.set("io.github.willywonka644.fintracker.db")
        }
    }
}
