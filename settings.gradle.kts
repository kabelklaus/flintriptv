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
plugins {
    // Resolves JVM toolchain downloads (Daemon JVM criteria in gradle/gradle-daemon-jvm.properties)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pins the native-code Jellyfin FFmpeg decoder to Maven Central specifically, so no
        // other (e.g. future) repository in this list can silently serve a substitute build.
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("org.jellyfin.media3") }
        }
    }
}

rootProject.name = "flintrtv"
include(":app")
