import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import solutions.laxmi.omnicompiler.buildlogic.configureKotlinAndroid
import solutions.laxmi.omnicompiler.buildlogic.intVersion
import solutions.laxmi.omnicompiler.buildlogic.libs

/** Standalone instrumentation-test modules (benchmarks, baseline profile generation) that drive the app. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        extensions.configure<TestExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.intVersion("targetSdk")
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
}
