import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
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
    alias(libs.plugins.kover)
}

android {
    namespace = "com.example.hybrid_ai_app"
    compileSdk = 36

    defaultConfig {
        // Deliberately different from `namespace` above: Play rejects the reserved "com.example"
        // prefix, and this is also the {packageName} the Play Developer API uses to validate
        // purchase receipts. The Kotlin package hierarchy stays com.example.hybrid_ai_app —
        // renaming it would touch every file for no functional gain.
        //
        // Changing this invalidates the GCP OAuth *Android* client (Google Sign-In) and the
        // Maps API key restriction, both of which are keyed on packageName + SHA-1.
        applicationId = "com.hybridai.training"
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
                "proguard-rules.pro",
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

    lint {
        // Android Lint reports 0 errors and 127 warnings on this codebase, so `abortOnError`
        // alone is a gate that cannot fail. `warningsAsErrors` makes it real; the baseline
        // freezes the existing 127 so only NEW warnings break the build.
        warningsAsErrors = true
        abortOnError = true
        baseline = file("lint-baseline.xml")

        // Disabled rather than baselined: these three compare the declared versions against
        // whatever is newest on the network TODAY, so a baselined snapshot of them would start
        // failing CI on an unrelated day when an upstream release happens. Dependency freshness
        // is a deliberate, reviewed decision here (see the billing note in libs.versions.toml),
        // not something a build gate should force.
        disable += setOf(
            "NewerVersionAvailable",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )

        xmlReport = true
        htmlReport = true
    }

    testOptions {
        unitTests {
            // Stubbed android.jar methods (android.util.Log, etc.) throw by default, which fails
            // any JVM test that walks a code path containing a log statement. Return defaults
            // instead so tests exercise error branches rather than the mocking framework.
            isReturnDefaultValues = true
        }
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
    // Google Play Billing (plain Java artifact — see the note in libs.versions.toml)
    implementation(libs.billing)

    // TESTING
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    // Semantic assertions on Flow/StateFlow (awaitItem) instead of hand-collecting into lists.
    testImplementation(libs.turbine)
    // Version comes from the okhttp-bom platform declared above, keeping MockWebServer on the
    // same OkHttp 4.12.0 as the production client.
    testImplementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    testImplementation(libs.okhttp.mockwebserver)
}
// Coverage gate. Scoped to the layers that are unit-testable on the JVM: view models, repositories,
// mappers, the remote/error-parsing layer, domain models and the Room type converters. Compose UI,
// Hilt modules, generated code and the Context/Play-Services-bound singletons are excluded — they
// are exercised by the app, not by JVM tests, and including them would only dilute the number.
kover {
    reports {
        // 80% floor on every unit Kover can bound, matching the backend's Jest
        // `coverageThreshold` of 80 across branches/functions/lines/statements. Kover 0.9 offers
        // LINE, BRANCH and INSTRUCTION as bounds (there is no FUNCTION unit); method coverage
        // currently sits at 100% and is visible in the HTML report.
        //
        // Measured on the filtered layers below: line 99.6%, branch 89.8%, instruction 98.9%.
        // The gate sits ~10 points under the weakest metric (branch) and ~19 under the others.
        // Deliberate headroom: a threshold set just beneath the current number fails on the next
        // unrelated refactor, which teaches everyone to lower the gate instead of writing a test.
        verify {
            rule("Logic layers stay covered") {
                minBound(80, CoverageUnit.LINE)
                minBound(80, CoverageUnit.BRANCH)
                minBound(80, CoverageUnit.INSTRUCTION)
            }
        }
        filters {
            includes {
                classes(
                    "com.example.hybrid_ai_app.*ViewModel",
                    "com.example.hybrid_ai_app.core.data.repository.*",
                    "com.example.hybrid_ai_app.core.data.remote.*",
                    "com.example.hybrid_ai_app.core.data.local.converter.*",
                    "com.example.hybrid_ai_app.core.data.EntitlementManager",
                    "com.example.hybrid_ai_app.core.domain.model.*",
                    "com.example.hybrid_ai_app.coach.data.*",
                    "com.example.hybrid_ai_app.home.data.mapper.*",
                    "com.example.hybrid_ai_app.home.domain.usecase.*",
                    "com.example.hybrid_ai_app.onboarding.data.PlanAttachment*",
                )
            }
            excludes {
                classes(
                    // Generated: Hilt, Room, kotlinx.serialization, BuildConfig.
                    "*_Factory",
                    "*_Factory\$*",
                    "*_MembersInjector",
                    "*_HiltModules*",
                    "*_Impl",
                    "*_GeneratedInjector",
                    "Hilt_*",
                    "*BuildConfig",
                    "*\$\$serializer",
                    // Dead stack: wired in HomeModule but consumed by no ViewModel. The pure
                    // parts (PlanMapper, GetActivePlanUseCase) stay covered above; the network
                    // half is excluded rather than hardened. Deletion is separate follow-up work.
                    "com.example.hybrid_ai_app.home.data.repository.*",
                    "com.example.hybrid_ai_app.home.data.remote.PlanApiService",
                    // Not JVM-testable: Context, DataStore, Android Keystore, Play Billing,
                    // ContentResolver/Bitmap. These are mocked collaborators in tests.
                    "com.example.hybrid_ai_app.core.data.PreferencesManager",
                    "com.example.hybrid_ai_app.core.data.PreferencesManager\$*",
                    "com.example.hybrid_ai_app.core.data.BillingManager",
                    "com.example.hybrid_ai_app.core.data.BillingManager\$*",
                    "com.example.hybrid_ai_app.onboarding.data.PlanAttachmentReader",
                    "com.example.hybrid_ai_app.onboarding.data.PlanAttachmentReader\$*",
                    // Pure data holders: @Serializable DTOs and their nested types. They contain
                    // no logic — every branch the coverage tool sees in them belongs to the
                    // compiler-generated `equals`, `hashCode` and `copy$default`, one null-check
                    // per property. UserDto alone contributes 74 such branches, and the only way
                    // to "cover" them is to assert that equals() distinguishes each of its 13
                    // fields in turn, which finds no defect and gates nothing.
                    //
                    // Their real contract — the wire names, the defaults, the null handling — is
                    // pinned far more strictly than coverage could, by SerialNameContractTest
                    // (every wire name, off the generated descriptors) and WireContractTest
                    // (decoding bodies transcribed from the backend). A DTO change breaks those
                    // tests loudly; it was never going to break a coverage threshold.
                    "com.example.hybrid_ai_app.core.data.remote.dto.*",
                    "com.example.hybrid_ai_app.core.data.remote.GeneratePlanRequest",
                    "com.example.hybrid_ai_app.core.data.remote.ImportPlanRequest",
                    "com.example.hybrid_ai_app.core.data.remote.PlanAttachmentDto",
                    "com.example.hybrid_ai_app.core.data.remote.GeneratePlanResponse",
                    "com.example.hybrid_ai_app.core.data.remote.ApiErrorDto",
                    "com.example.hybrid_ai_app.coach.data.ChatRequest",
                    "com.example.hybrid_ai_app.coach.data.ChatResponse",
                    "com.example.hybrid_ai_app.coach.data.ChatData",
                    "com.example.hybrid_ai_app.coach.data.ChatMessageDto",
                    "com.example.hybrid_ai_app.coach.data.presentation.*",
                    "com.example.hybrid_ai_app.auth.data.remote.*",
                    "com.example.hybrid_ai_app.onboarding.data.remote.dto.*",
                    "com.example.hybrid_ai_app.home.data.remote.dto.*",
                    "com.example.hybrid_ai_app.home.domain.model.*",
                    "com.example.hybrid_ai_app.core.data.local.entity.*",
                    "com.example.hybrid_ai_app.onboarding.data.PlanAttachment",
                    "com.example.hybrid_ai_app.onboarding.data.PlanAttachmentException",
                )
            }
        }
    }
}
