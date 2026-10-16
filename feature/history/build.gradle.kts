plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.history"
}

dependencies {
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(projects.core.ads)
    implementation(projects.core.editor)
    implementation(libs.androidx.paging.compose)
}
