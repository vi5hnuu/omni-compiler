plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
