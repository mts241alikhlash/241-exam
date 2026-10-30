pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RootEncoder is published to JitPack only.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "241-exam"
include(":app")
