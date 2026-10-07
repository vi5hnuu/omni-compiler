plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.projects"
}

dependencies {
    implementation(projects.core.ads)
    implementation(libs.markdown.renderer.m3)
}
