pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "household-platform"

// Include library modules
include(":libs:household-core")

// Include feature modules
include(":modules:expenses")

// Include app module
include(":android")

// Astro sub-system modules
include(":core:security")
include(":core:time")
include(":core:ephemeris")
include(":core:ai-runtime")
include(":feature:astro")
include(":widget:astro-home")
include(":core:llm-runtime")
include(":core:document-ai")
include(":feature:assistant")
