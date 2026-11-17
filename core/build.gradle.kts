plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // The typing tests decode swipes against the real bundled word list.
    val wordList = rootProject.file("app/src/main/assets/words_en.txt")
    inputs.file(wordList)
    systemProperty("voxkeys.wordlist", wordList.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
