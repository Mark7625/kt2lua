plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation("kt2lua:transpiler:0.1.0")
    implementation("kt2lua:interpreter:0.1.0")
}

application {
    mainClass.set("kt2lua.cli.MainKt")
}
