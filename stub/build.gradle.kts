plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "io.github.chsbuffer.revancedxposed.spotify.stub"
    compileSdk = 34  // Obligatoire

    defaultConfig {
        minSdk = 21
        // targetSdk pour les tests unitaires
        testOptions {
            unitTests.all {
                it.targetSdk = 34
            }
        }
        // targetSdk pour lint
        lint {
            targetSdk = 34
        }
        // versionCode et versionName sont supprimés (library)
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
