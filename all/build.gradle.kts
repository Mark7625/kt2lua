plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.maven.publish)
}

val bundledModules = listOf("common", "transpiler", "interpreter", "cli", "gradle-plugin")

sourceSets.main {
    kotlin.srcDirs(bundledModules.map { rootProject.file("$it/src/main/kotlin") })
    resources.srcDirs(bundledModules.map { rootProject.file("$it/src/main/resources") })
}

dependencies {
    implementation(libs.gson)
}

gradlePlugin {
    plugins {
        create("kt2lua") {
            id = "io.github.mark7625.kt2lua"
            implementationClass = "kt2lua.gradle.Kt2LuaPlugin"
        }
    }
}
