import org.gradle.api.initialization.resolve.RepositoriesMode

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    // Same catalog file the main build's settings.gradle.kts uses — single source of
    // truth for the Kotlin/JDK versions, shared across both Gradle builds (this is a
    // separate `includeBuild`-ed build, so it can't just inherit the main build's catalog).
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "kt2lua-build-logic"

include("common", "transpiler", "interpreter", "gradle-plugin")
