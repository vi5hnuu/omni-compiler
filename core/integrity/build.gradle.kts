plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.integrity"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    // Google Play Integrity (standard requests), plus coroutines' Task.await().
    implementation(libs.play.integrity)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)
}
