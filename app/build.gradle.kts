plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "io.github.chsbuffer.revancedxposed.spotify"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.chsbuffer.revancedxposed.spotify"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Pour Xposed Hook, pas de multiDex
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

repositories {
    google()
    mavenCentral() // Obligatoire pour dnsjava
    maven { url = uri("https://jitpack.io") } // si besoin pour dépendances Xposed
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.xbill:dnsjava:3.5.2") // Version stable compatible Maven Central
    // Ajoute ici tes dépendances Xposed / ReVanced nécessaires
}
