import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import solutions.laxmi.omnicompiler.buildlogic.lib
import solutions.laxmi.omnicompiler.buildlogic.libs

/**
 * A feature module: an Android library with Compose + Hilt that depends only on core modules,
 * never on other features. Cross-feature navigation goes through :core:navigation keys.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("omni.android.library")
        pluginManager.apply("omni.android.compose")
        pluginManager.apply("omni.hilt")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:navigation"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
        }
    }
}
