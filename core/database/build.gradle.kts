plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
    alias(libs.plugins.omni.room)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.room.paging)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
