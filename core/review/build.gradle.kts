plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.android.compose)
    alias(libs.plugins.omni.hilt)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.review"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    // The base library plus coroutines' Task.await(); review-ktx references a Play annotation class R8 can't find.
    implementation(libs.play.review)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
}
