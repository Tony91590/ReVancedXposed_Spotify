plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "io.github.chsbuffer.revancedxposed.spotify.stub"
    compileSdk = 34  // Obligatoire

    defaultConfig {
        minSdk = 21
        // Pour une library, pas besoin de targetSdk, versionCode ou versionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("de.robv.android.xposed:api:82") // Xposed API
}
