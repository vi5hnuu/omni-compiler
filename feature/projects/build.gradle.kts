plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.projects"
}

dependencies {
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(projects.core.ads)
    implementation(libs.markdown.renderer.m3)
}
