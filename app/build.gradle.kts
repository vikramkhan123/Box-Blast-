plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example"
    compileSdk = 36  // Yahan 35 se 36 kiya hai

    defaultConfig {
        applicationId = "com.example"
        minSdk = 24
        targetSdk = 36 // Yahan bhi 35 se 36 kiya hai
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
