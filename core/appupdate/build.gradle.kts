plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.appupdate"
}

dependencies {
    // Google Play in-app updates, plus coroutines' Task.await().
    implementation(libs.play.app.update)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.timber)
}
