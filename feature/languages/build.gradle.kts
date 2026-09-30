plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.languages"
}

dependencies {
    implementation(libs.androidx.compose.material3)
}
