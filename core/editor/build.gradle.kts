plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.android.compose)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.editor"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
