// group/version come from the root build.gradle.kts's kt2lua-modules configure block.
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
