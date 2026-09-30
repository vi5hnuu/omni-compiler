plugins {
    alias(libs.plugins.omni.android.application)
    alias(libs.plugins.omni.android.compose)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.aboutlibraries.android)
}

// Open-source notices are generated from dependency metadata at build time (res/raw/aboutlibraries.json);
// components Gradle can't see (fonts, embedded tm4e, TextMate grammars) are declared in config/aboutlibraries.
aboutLibraries {
    collect {
        configPath = rootProject.file("config/aboutlibraries")
    }
}

android {
    namespace = "solutions.laxmi.omnicompiler"

    defaultConfig {
        applicationId = "solutions.laxmi.omnicompiler"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // en-XA / ar-XB in developer settings reveal untranslated or clipped text.
            isPseudoLocalesEnabled = true
        }
        release {
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)

    implementation(projects.feature.auth)
    implementation(projects.feature.workspace)
    implementation(projects.feature.languages)
    implementation(projects.feature.projects)
    implementation(projects.feature.history)
    implementation(projects.feature.account)
    implementation(projects.feature.developer)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
