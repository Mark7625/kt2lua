plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":transpiler"))
    implementation(project(":interpreter"))
}

application {
    mainClass.set("kt2lua.cli.MainKt")
}
