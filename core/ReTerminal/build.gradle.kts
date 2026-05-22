plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.compiler)
}

fun gitValue(vararg args: String): String {
    val result = providers.exec {
        isIgnoreExitValue = true
        commandLine("git", *args)
    }
    return result.standardOutput.asText.get().trim().ifEmpty { "unknown" }
}

val gitCommitHash = gitValue("rev-parse", "--short=8", "HEAD")
val fullGitCommitHash = gitValue("rev-parse", "HEAD")
val gitCommitDate = gitValue("show", "-s", "--format=%cI", "HEAD")

android {
    namespace = "com.rk.terminal"
    android.buildFeatures.buildConfig = true
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"$fullGitCommitHash\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"$gitCommitHash\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"$gitCommitDate\"")

            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
        debug{
            buildConfigField("String", "GIT_COMMIT_HASH", "\"$fullGitCommitHash\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"$gitCommitHash\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"$gitCommitDate\"")
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(project(":core:terminal-view"))
    implementation(project(":core:terminal-emulator"))
    implementation(libs.utilcode)
    implementation(libs.anrwatchdog)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.palette)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.okhttp)
    implementation(project(":core:resources"))
    implementation(project(":core:components"))
}
