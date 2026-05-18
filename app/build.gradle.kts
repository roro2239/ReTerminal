plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.rk.application"
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
        applicationId = "com.rk.terminal"
        minSdk = 26
        targetSdk = 36

        //versioning
        versionCode = 8
        versionName = "1.2.1"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["mainNamespace"] = "com.rk.terminal"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
