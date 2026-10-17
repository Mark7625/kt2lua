import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Same group/version for all three of this composite build's modules (transpiler/interpreter/
// gradle-plugin) — declared once here instead of three times. Kept separate from the main
// build's own `group`/`version` handling (root build.gradle.kts) since this is a genuinely
// different Gradle build (`includeBuild`-ed), not a subproject of it.
subprojects {
    group = "kt2lua"
    version = "0.1.0"

    // Same JDK across both Gradle builds in this repo — see gradle/libs.versions.toml (the
    // single place this version is declared) and this build's own settings.gradle.kts for why
    // it has to point at that file explicitly rather than just inheriting it.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(libs.versions.jdk.get().toInt())
        }
    }
}
