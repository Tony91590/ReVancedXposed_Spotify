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

    // Nouveau DSL recommandé pour Kotlin compilerOptions
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

repositories {
    mavenCentral() // dnsjava disponible ici
    maven { url = uri("https://jitpack.io") } // si besoin pour Xposed
    // Retirer google() si tu utilises settings repositories
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.xbill:dnsjava:3.5.2")
    // Xposed / ReVanced dépendances ici si nécessaire
}
