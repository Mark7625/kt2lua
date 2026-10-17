import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kt2lua"

includeBuild("build-logic")

include("cli", "test-runner")

// `libs/runescape/*` isn't tracked by this repo's own git history (see .gitignore) — it's
// slated to move to its own repo eventually, kept on disk here only in the meantime. Including
// it conditionally means the kt2lua build itself (cli/test-runner/build-logic) stays buildable
// on a fresh checkout — CI included — whether or not that directory happens to exist locally;
// see the root build.gradle.kts's own `if (file("libs/runescape").exists())` guard around the
// configuration that applies specifically to those modules.
if (file("libs/runescape").exists()) {
    include("libs:runescape:core", "libs:runescape:extended", "libs:runescape:prettyui")
    include("libs:runescape:examples:prettyui-window-kotlin")
}
