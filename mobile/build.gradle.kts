plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bachir.khutwa.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bachir.sapphire"
        minSdk = 28
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.maplibre.gl:android-sdk:11.8.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.media:media:1.7.0")
}
