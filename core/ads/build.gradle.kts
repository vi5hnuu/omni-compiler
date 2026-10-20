import java.util.Properties

plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.android.compose)
    alias(libs.plugins.omni.hilt)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun config(key: String): String = (localProps.getProperty(key) ?: providers.gradleProperty(key).orNull).orEmpty()

android {
    namespace = "solutions.laxmi.omnicompiler.core.ads"
    buildFeatures.buildConfig = true
    buildTypes {
        // Debug builds only ever request Google's test ads, so real ads can't be clicked by mistake while testing.
        // Debug also drops the first-day grace and shortens the gaps, so full-screen ads can be seen while testing.
        debug {
            buildConfigField("String", "BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "REWARDED_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "NATIVE_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "APP_OPEN_UNIT_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("long", "INTERSTITIAL_GRACE_MS", "0L")
            buildConfigField("long", "INTERSTITIAL_MIN_INTERVAL_MS", "60000L")
            buildConfigField("long", "APP_OPEN_AWAY_MS", "60000L")
        }
        release {
            buildConfigField("String", "BANNER_UNIT_ID", "\"${config("omni.admob.bannerId")}\"")
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", "\"${config("omni.admob.interstitialId")}\"")
            buildConfigField("String", "REWARDED_UNIT_ID", "\"${config("omni.admob.rewardedId")}\"")
            buildConfigField("String", "NATIVE_UNIT_ID", "\"${config("omni.admob.nativeId")}\"")
            buildConfigField("String", "APP_OPEN_UNIT_ID", "\"${config("omni.admob.appOpenId")}\"")
            buildConfigField("long", "INTERSTITIAL_GRACE_MS", "86400000L")
            buildConfigField("long", "INTERSTITIAL_MIN_INTERVAL_MS", "180000L")
            buildConfigField("long", "APP_OPEN_AWAY_MS", "14400000L")
        }
    }
    // How long a watched video keeps full-screen ads away (two hours).
    defaultConfig.buildConfigField("long", "PASS_DURATION_MS", "7200000L")
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    api(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}
