import java.util.Properties

// Load local.properties
val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
}
// Get the token, fallback to empty string if not found
val apiToken = properties.getProperty("API_TOKEN") ?: ""
// Google OAuth Web Client ID — kept out of source (git-ignored local.properties)
val webClientId = properties.getProperty("WEB_CLIENT_ID") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    // Relies safely on the version catalog (TOML)
    alias(libs.plugins.ksp)
    // Matches your exact Kotlin version
    kotlin("plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.hybrid_ai_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.hybrid_ai_app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["googleApiKey"] = apiToken
        // Exposed via BuildConfig instead of hardcoding in source
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX & Compose Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Google Play Services & Auth
    implementation(libs.play.services.auth)
    implementation(libs.play.services.places)
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // Google Maps & Location
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted storage for the JWT (AES256, key backed by Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Dagger Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.51.1")
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")


    // OkHttp BOM & Interceptors
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // Kotlinx Serialization (For Gemini AI responses)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.5.0")
    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.2.0") // Or latest stable

    //TESTING
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}