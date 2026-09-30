plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.auth"
}

dependencies {
    implementation(projects.core.editor)
}
