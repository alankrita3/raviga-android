// R8 bundled with AGP 8.7 can't read Kotlin 2.3/2.4 metadata (Firebase Analytics 24 ships 2.4);
// R8 9.1.29+ can (developer.android.com/build/kotlin-support). Drop this when AGP moves past 9.1.
buildscript {
    repositories { google() }
    dependencies { classpath("com.android.tools:r8:9.5.28") }
}

// Top-level build file. Versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}
