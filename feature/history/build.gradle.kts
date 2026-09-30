plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.history"
}

dependencies {
    implementation(projects.core.ads)
    implementation(projects.core.editor)
    implementation(libs.androidx.paging.compose)
}
