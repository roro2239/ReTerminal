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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }


}

dependencies {
    api(libs.appcompat)
    api(libs.material)
    api(libs.constraintlayout)
    api(libs.navigation.fragment)
    api(libs.navigation.ui)
    api(libs.navigation.fragment.ktx)
    api(libs.navigation.ui.ktx)
    api(libs.activity)
    api(libs.lifecycle.viewmodel.ktx)
    api(libs.lifecycle.runtime.ktx)
    api(libs.activity.compose)
    api(platform(libs.compose.bom))
    api(libs.ui)
    api(libs.ui.graphics)
    api(libs.material3)
    api(libs.navigation.compose)
    api(project(":core:terminal-view"))
    api(project(":core:terminal-emulator"))
    api(libs.utilcode)
    //api(libs.commons.net)
    api(libs.anrwatchdog)
    api(libs.androidx.material.icons.core)
    api(libs.androidx.palette)
    api(libs.accompanist.systemuicontroller)
    api(libs.okhttp)
//    api(libs.termux.shared)

    api(project(":core:resources"))
    api(project(":core:components"))
}
