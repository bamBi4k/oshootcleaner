plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.bambi4k.oshootcleaner"
    compileSdk = 34

    lint {
        // Shizuku uses hidden system APIs intentionally.
        // PrivateApi is the correct lint ID to suppress.
        disable += "PrivateApi"
        // Keep RestrictedApi disabled too — AndroidX internals
        // are used by Compose/Glance.
        disable += "RestrictedApi"
    }

    defaultConfig {
        // FIXED: must match namespace exactly for F-Droid.
        applicationId = "io.github.bambi4k.oshootcleaner"
        minSdk = 26
        targetSdk = 34

        // FIXED: bumped for v1.0.1
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            // F-Droid signs the APK itself — do NOT add a signingConfig here.
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Requires Kotlin 1.9.24 in the root build.gradle.kts
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Glance: Compose-style API for building home-screen widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // Backing store Glance uses to persist widget state between updates
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Shizuku API for calling privileged system services
    implementation("dev.rikka.shizuku:api:13.1.5")
    // Shizuku Provider for the IPC bridge
    implementation("dev.rikka.shizuku:provider:13.1.5")
}