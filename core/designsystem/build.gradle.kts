plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.android.compose)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.graphics)
}
