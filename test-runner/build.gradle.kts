plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(project(":transpiler"))
    testImplementation(project(":interpreter"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotlin.compiler.embeddable)
}

tasks.test {
    useJUnitPlatform {
        // Differential tests shell out to the real Kotlin compiler and are opt-in via
        // `differentialTest` below — they'd otherwise add several seconds to every `test` run.
        excludeTags("differential")
    }
}

tasks.register<Test>("differentialTest") {
    group = "verification"
    description = "Compiles and runs each fixture under src/test/resources/differential-fixtures " +
        "on the real JVM, transpiles and runs it through kt2lua's Lua interpreter, and compares " +
        "their output. Filter with -Pcategory=<name>."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("differential")
    }
    project.findProperty("category")?.let { systemProperty("kt2lua.differential.category", it) }
}
