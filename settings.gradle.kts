pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BlueScopeAI"

include(
    ":app",
    ":core:domain",
    ":core:bluetooth",
    ":core:database",
    ":core:ai",
    ":core:data",
    ":core:designsystem",
)
