plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":transpiler"))
    implementation(project(":interpreter"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("kt2lua.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
