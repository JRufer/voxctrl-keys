plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// One version, from gradle.properties. versionCode is derived from it
// (0.1.0 -> 100, 1.4.12 -> 10412) so the two cannot drift apart: Android
// installs an update only when versionCode has gone up.
val versionNameValue = providers.gradleProperty("VERSION_NAME").get()
val versionCodeValue = versionNameValue.substringBefore('-').split('.').let { parts ->
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null } && parts[1].toInt() < 100 && parts[2].toInt() < 100) {
        "VERSION_NAME must be MAJOR.MINOR.PATCH with MINOR and PATCH below 100, got '$versionNameValue'"
    }
    parts[0].toInt() * 10_000 + parts[1].toInt() * 100 + parts[2].toInt()
}

// The release signing key never lives in the repository. A release build is
// signed only when these are set (the release workflow sets them from
// repository secrets; see docs/release-signing.md). Without them,
// assembleRelease still compiles but its APK is unsigned and cannot be
// installed.
val releaseKeystore: String? = System.getenv("VOXKEYS_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "ai.voxctrl.keys"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.voxctrl.keys"
        // Android 10. The keyboard relies on switchToPreviousInputMethod (28)
        // and the system day/night theme (29); fold awareness needs 30 and is
        // skipped below that.
        minSdk = 29
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    signingConfigs {
        releaseKeystore?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("VOXKEYS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VOXKEYS_KEY_ALIAS")
                keyPassword = System.getenv("VOXKEYS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.java)
}
