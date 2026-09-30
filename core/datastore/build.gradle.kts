plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
}
