import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json is not committed (see .gitignore). Apply the Firebase plugin
// only when the file is present so a fresh clone still builds. Push notifications
// need the real file; everything else works without it.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// The upload key lives outside the repo; keystore.properties (gitignored) points at
// it. Without the file a release build is simply unsigned, so a fresh clone builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

// secrets.properties (gitignored) holds per-environment values. Every key has a
// placeholder default so the app builds and runs in demo mode without the file.
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
fun secret(key: String, default: String): String = secrets.getProperty(key) ?: default

// `-Praviga.internalTest=true`: a release bundle for Play's internal testing track only, before prod
// exists. It talks to the dev stage (API_BASE_URL_DEBUG) and may lack the RevenueCat key; it is still
// signed with the upload key and never falls back to the in-app demo backend.
val internalTest: Boolean = (findProperty("raviga.internalTest") as String?) == "true"

android {
    namespace = "com.raviga.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raviga.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Backend base URL, e.g. https://xxxx.execute-api.ap-south-1.amazonaws.com/v1/
        // Empty means "no backend yet": the app runs against the built-in demo backend
        // so every screen can be exercised before the API is deployed.
        buildConfigField("String", "API_BASE_URL", "\"${secret("API_BASE_URL", "")}\"")
        // RevenueCat public Android SDK key (public by design, meant to be embedded).
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${secret("REVENUECAT_API_KEY", "")}\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"${secret("API_BASE_URL_DEBUG", secret("API_BASE_URL", ""))}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (internalTest) buildConfigField("String", "API_BASE_URL", "\"${secret("API_BASE_URL_DEBUG", "")}\"")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Contract v0.9 section 22: usage analytics, off until the client consents. Never the advertising id.
    implementation(libs.firebase.analytics) {
        exclude(group = "com.google.android.gms", module = "play-services-ads-identifier")
    }
    // RevenueCat reads the advertising id only if this optional module is present (contract v0.9: never).
    implementation(libs.revenuecat.purchases) {
        exclude(group = "com.google.android.gms", module = "play-services-ads-identifier")
    }
    // Documents are read on the phone (local-first): text PDFs via PDFBox, scans via
    // ML Kit's on-device recogniser (model delivered by Play services; text never leaves).
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// A Play upload must never ship the demo backend, an unsigned bundle or a store
// without products. assembleRelease stays unguarded for local R8 checks.
val releaseMissing: String = listOfNotNull(
    "API_BASE_URL in secrets.properties".takeIf { !internalTest && secret("API_BASE_URL", "").isBlank() },
    "API_BASE_URL_DEBUG (the dev stage) in secrets.properties".takeIf { internalTest && secret("API_BASE_URL_DEBUG", "").isBlank() },
    "REVENUECAT_API_KEY in secrets.properties".takeIf { !internalTest && secret("REVENUECAT_API_KEY", "").isBlank() },
    "app/google-services.json".takeIf { !file("google-services.json").exists() },
    "keystore.properties".takeIf { !keystorePropertiesFile.exists() },
).joinToString("; ")
val checkReleaseReadiness = tasks.register("checkReleaseReadiness") {
    val missing = releaseMissing
    doLast {
        if (missing.isNotEmpty()) throw GradleException("A Play bundle needs: $missing. See docs/HANDOFF.md.")
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(checkReleaseReadiness) }
