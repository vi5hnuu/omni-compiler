import java.util.Properties

plugins {
    alias(libs.plugins.omni.android.application)
    alias(libs.plugins.omni.android.compose)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.baselineprofile)
}

// Open-source notices are generated from dependency metadata at build time (res/raw/aboutlibraries.json);
// components Gradle can't see (fonts, embedded tm4e, TextMate grammars) are declared in config/aboutlibraries.
aboutLibraries {
    collect {
        configPath = rootProject.file("config/aboutlibraries")
    }
    library {
        // Only open-source code the app ships. Google Play services, AdMob/UMP, Google ID and Play's hsdp are
        // proprietary (Android SDK licence / Google terms); BOMs are version lists and the Dagger lint AAR isn't
        // packaged, so neither carries code to attribute.
        exclusionPatterns = setOf(
            Regex("com\\.google\\.android\\.gms:.*").toPattern(),
            Regex("com\\.google\\.android\\.ump:.*").toPattern(),
            Regex("com\\.google\\.android\\.libraries\\.identity\\..*").toPattern(),
            Regex("com\\.google\\.android\\.play:hsdp").toPattern(),
            Regex(".*:.*-bom").toPattern(),
            Regex("com\\.google\\.dagger:dagger-lint-aar").toPattern(),
        )
    }
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

/** Google's sample AdMob app id: only ever serves test ads. Release uses `omni.admob.appId` from local.properties. */
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"

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
            manifestPlaceholders["admobAppId"] = testAdMobAppId
            // en-XA / ar-XB in developer settings reveal untranslated or clipped text.
            isPseudoLocalesEnabled = true
        }
        release {
            // Until the real id is set, release keeps the test app id; its ad units are empty, so it requests no ads.
            manifestPlaceholders["admobAppId"] = localProps.getProperty("omni.admob.appId") ?: testAdMobAppId
            // R8 code and resource shrinking. Kept on these flags (rather than AGP 9's `optimization { enable }`) because the
            // Baseline Profile plugin turns exactly these off for its non-minified profiling variant; with `optimization`
            // that variant stayed obfuscated and produced a profile whose class names don't match this build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    implementation(projects.feature.vcs)
    implementation(projects.core.ads)

    // Installs the bundled baseline profile so first launches run precompiled code paths.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.timber)
    baselineProfile(projects.baselineprofile)

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
