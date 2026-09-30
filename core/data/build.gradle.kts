import java.util.Properties

plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun config(key: String): String =
    (localProps.getProperty(key) ?: providers.gradleProperty(key).orNull).orEmpty()

android {
    namespace = "solutions.laxmi.omnicompiler.core.data"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${config("omni.googleWebClientId")}\"")
        buildConfigField("String", "LEGAL_BASE_URL", "\"${config("omni.legalBaseUrl")}\"")
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
    implementation(projects.core.catalog)
    api(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.androidx.browser)
    testImplementation(libs.okhttp.mockwebserver)
}
