import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import solutions.laxmi.omnicompiler.buildlogic.configureKotlinAndroid
import solutions.laxmi.omnicompiler.buildlogic.lib
import solutions.laxmi.omnicompiler.buildlogic.libs

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // Each library ships its own keep rules to the app's R8 pass.
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
