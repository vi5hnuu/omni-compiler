plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.workspace"
}

dependencies {
    implementation(projects.core.editor)
    implementation(libs.androidx.compose.material3)
}
