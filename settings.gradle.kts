pluginManagement {
    repositories {
        google() // ✅ Ensures Google repository is available
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage") // Suppress the warning
    repositories {
        google() // ✅ Required for Firebase & Play Services
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            library("retrofit", "com.squareup.retrofit2:retrofit:2.9.0")
            library("coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        }
    }
}

// ✅ Define the project name
rootProject.name = "SafeGuard"

// ✅ Include app module
include(":app")

