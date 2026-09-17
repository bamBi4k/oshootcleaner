plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.oshootcleaner"
    compileSdk = 34

    lint {
        disable += "RestrictedApi"
        // or, more targeted:
        // disable += "PrivateApi"
    }

    defaultConfig {
        applicationId = "com.example.oshootcleaner"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
