// group/version come from build-logic/build.gradle.kts's subprojects block.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":transpiler"))
}

gradlePlugin {
    plugins {
        create("kt2lua") {
            id = "kt2lua.transpile"
            implementationClass = "kt2lua.gradle.Kt2LuaPlugin"
        }
    }
}
