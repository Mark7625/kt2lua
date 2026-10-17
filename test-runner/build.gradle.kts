plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kt2lua.transpile")
}

dependencies {
    testImplementation("kt2lua:interpreter:0.1.0")
    testImplementation(kotlin("test-junit5"))
}

kt2lua {
    sourceDir.set(layout.projectDirectory.dir("src/test/resources/examples"))
    outputDir.set(layout.buildDirectory.dir("generated/kt2lua"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn("transpileKotlinToLua")
}
