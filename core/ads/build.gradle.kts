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
        debug {
            buildConfigField("String", "BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            buildConfigField("String", "BANNER_UNIT_ID", "\"${config("omni.admob.bannerId")}\"")
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", "\"${config("omni.admob.interstitialId")}\"")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    api(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
