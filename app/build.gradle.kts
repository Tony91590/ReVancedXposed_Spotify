plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    compileSdk 34

    defaultConfig {
        applicationId "io.github.chsbuffer.revancedxposed"
        minSdk 24
        targetSdk 34
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {
    compileOnly 'de.robv.android.xposed:api:82'
    implementation "dnsjava:dnsjava:3.5.2"
}
