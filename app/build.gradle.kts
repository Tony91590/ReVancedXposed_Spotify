plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "io.github.chsbuffer.revancedxposed.spotify.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.chsbuffer.revancedxposed.spotify.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packagingOptions {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE")
        }
    }
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repo.xposed.info") } // Dépôt officiel Xposed
    maven { url = uri("https://jitpack.io") }       // JitPack pour ReVanced libs
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("org.xbill:dnsjava:3.5.2")                 // version stable disponible
    implementation("de.robv.android.xposed:api:82")          // Xposed API (vérifie existence sur repo.xposed.info)
    // Ajoute ici tes autres libs ReVanced si besoin
}
