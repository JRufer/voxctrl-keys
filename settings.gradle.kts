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
    }
}

rootProject.name = "voxctrl-keys"

// core: everything that is not Android (the server client, pairing codes,
//       layout rules). Plain Kotlin, tested on the JVM.
// app:  the Android keyboard itself (arrives with the first pull request).
include(":core")
