plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.settings"
    buildFeatures.buildConfig = false
}

dependencies {
    implementation(projects.core.editor)
}
