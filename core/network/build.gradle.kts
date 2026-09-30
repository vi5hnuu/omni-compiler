import java.util.Properties

plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// gradle.properties holds shared defaults; local.properties may override them per machine.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun config(key: String): String =
    (localProps.getProperty(key) ?: providers.gradleProperty(key).orNull).orEmpty()

android {
    namespace = "solutions.laxmi.omnicompiler.core.network"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"${config("omni.apiBaseUrl")}\"")
        buildConfigField("String", "AUTH_BASE_URL", "\"${config("omni.authBaseUrl")}\"")
        buildConfigField("String", "JWT_AUDIENCE", "\"${config("omni.jwtAudience")}\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
