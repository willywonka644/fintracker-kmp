plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Makes the module startable: `:server:run` for development, and an
    // installDist/distZip that #93 can put on the Pi. Until now the only way to
    // start the server was the IDE's gutter arrow, which is no way to run a
    // service and no way to reproduce a start on another machine.
    application
}

application {
    mainClass.set("io.github.willywonka644.fintracker.server.MainKt")
}

// The API key has to reach the forked JVM (#89: no key, no start).
//
// Plain inheritance is not enough here: the Gradle daemon outlives the shell and
// keeps the environment it was started with, so a variable exported afterwards
// would silently not arrive and the server would refuse to start for a reason
// that looks like a bug. The provider API reads it from the current build
// invocation instead — that is, from whoever just typed the command.
tasks.named<JavaExec>("run") {
    providers.environmentVariable("FINTRACKER_API_KEY").orNull
        ?.let { environment("FINTRACKER_API_KEY", it) }
}

dependencies {
    // The shared module is a Kotlin Multiplatform library with an Android and a
    // jvm("desktop") target. A plain kotlin-jvm consumer asks for the jvm
    // platform, so Gradle resolves the desktop target — no Android artefacts
    // come along. That is what lets the server reuse mergeEntities and the
    // repositories instead of reimplementing them (#88).
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // JDBC driver: the same one the desktop uses, pointed at the server's own
    // database file rather than the user's.
    implementation(libs.sqldelight.sqlite.driver)

    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

// No explicit toolchain, matching :desktopApp — both follow the Gradle JVM, and
// CI pins that to 17.
