plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.catalog"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.serialization.json)
}
