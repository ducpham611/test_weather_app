// Animated weather backgrounds, taken from Breezy Weather (LGPL-3.0).
// Kept as its own Gradle module so the LGPL code stays clearly separated from
// the app's own sources - see ui-weather-view/README.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.breezyweather.ui.theme.weatherView"
    compileSdk = 35

    defaultConfig {
        // Breezy itself ships minSdk 24, but this module only touches Canvas,
        // Paint, ObjectAnimator and the sensor APIs, all present since API 21.
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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
    implementation("androidx.core:core-ktx:1.13.1")
}
