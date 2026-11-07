plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bachir.khutwa.wear"
    compileSdk = 36

    defaultConfig {
        // Must match the phone app id so the Data Layer connects them.
        applicationId = "com.bachir.sapphire"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    signingConfigs {
        create("sapphire") {
            storeFile = rootProject.file("sapphire.keystore")
            storePassword = "sapphire"
            keyAlias = "sapphire"
            keyPassword = "sapphire"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sapphire") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sapphire")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.health:health-services-client:1.1.0-alpha05")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
