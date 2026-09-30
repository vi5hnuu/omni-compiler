plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.android.compose)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
