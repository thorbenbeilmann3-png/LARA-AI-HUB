plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.laraai.hub"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.laraai.hub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Identifies the build in the connection report (commit only, no secrets).
        buildConfigField("String", "GIT_SHA", "\"${(System.getenv("GITHUB_SHA") ?: "lokal").take(7)}\"")
        manifestPlaceholders["appLabel"] = "LARA AI HUB"
    }

    signingConfigs {
        getByName("debug") {
            // CI only: the fixed key from the secret LARA_CI_KEYSTORE_B64, see docs/FESTE-SIGNATUR.md.
            System.getenv("LARA_CI_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { storeFile = file(it) }
        }
    }

    buildTypes {
        getByName("debug") {
            // Test builds install next to an existing LARA and cannot replace it or its stored keys.
            if (System.getenv("LARA_SIDE_BY_SIDE") == "1") {
                applicationIdSuffix = ".alpinetest"
                versionNameSuffix = "-alpinetest"
                manifestPlaceholders["appLabel"] = "LARA Test"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // BiometricPrompt needs FragmentActivity; pin a fragment version matching activity 1.9.x.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM (Android's stub throws in local unit tests).
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
