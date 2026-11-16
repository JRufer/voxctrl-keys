plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

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
        // Bump both for every change that is going to be released, and add
        // docs/release-notes/<versionName>.md.
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
