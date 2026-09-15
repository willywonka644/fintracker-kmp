pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FinTracker"
include(":app")
include(":shared")
include(":desktopApp")
// Phase 7 (#88): the sync server that runs on the Raspberry Pi. Plain JVM — it
// consumes the shared module's jvm target, the same one the desktop app uses.
include(":server")
