plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.vcs"
}

dependencies {
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
}
