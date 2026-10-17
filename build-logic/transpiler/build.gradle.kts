// group/version come from build-logic/build.gradle.kts's subprojects block.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":common"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
