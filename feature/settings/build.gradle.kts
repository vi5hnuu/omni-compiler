plugins {
    alias(libs.plugins.omni.android.feature)
}

android {
    namespace = "solutions.laxmi.omnicompiler.feature.settings"
    buildFeatures.buildConfig = false
}

dependencies {
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(projects.core.ads)
    implementation(projects.core.review)
    implementation(projects.core.editor)
}

dependencies {
    implementation(libs.aboutlibraries.core)
}
