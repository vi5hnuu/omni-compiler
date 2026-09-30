plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.workspace"
}

dependencies {
    implementation(projects.core.ads)
    implementation(projects.core.editor)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.markdown.renderer.m3)
}
