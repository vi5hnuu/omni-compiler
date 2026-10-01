plugins {
    alias(libs.plugins.omni.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "solutions.laxmi.omnicompiler.baselineprofile"
    // Baseline profile generation and macrobenchmarks need API 28+.
    defaultConfig.minSdk = 28
    targetProjectPath = ":app"
}

// Profiles and benchmarks run on a connected phone (generation works without root on Android 13+).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
