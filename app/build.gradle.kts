plugins {
    alias(libs.plugins.android.application)
    // Extra unused plugins disabled to fix missing google-services & KSP errors
    // alias(libs.plugins.kotlin.compose)
    // alias(libs.plugins.google.devtools.ksp)
    // alias(libs.plugins.roborazzi)
    // alias(libs.plugins.secrets)
    // alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
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
    implementation(libs.androidx.core.ktx)
    // "ksp"(libs.androidx.room.compiler)
    // "ksp"(libs.moshi.kotlin.codegen)
}
