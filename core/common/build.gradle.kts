plugins {
    alias(libs.plugins.omni.android.library)
    alias(libs.plugins.omni.hilt)
}

android {
    namespace = "solutions.laxmi.omnicompiler.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
