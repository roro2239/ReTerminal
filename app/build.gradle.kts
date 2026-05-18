plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.roro.terminal"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release{
            isMinifyEnabled = false
            isCrunchPngs = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    
    defaultConfig {
        applicationId = "com.roro.terminal"
        minSdk = 26
        targetSdk = 36

        //versioning
        versionCode = 8
        versionName = "1.2.1"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:ReTerminal"))
}
