pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "omnicompiler"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":baselineprofile")

include(":core:common")
include(":core:model")
include(":core:navigation")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:catalog")
include(":core:storage")
include(":core:ads")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:editor")

include(":feature:auth")
include(":feature:workspace")
include(":feature:languages")
include(":feature:projects")
include(":feature:history")
include(":feature:account")
include(":feature:developer")
include(":feature:settings")
include(":feature:vcs")
