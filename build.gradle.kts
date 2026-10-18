import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Every subproject builds against the exact same JDK, not just the same bytecode *target* —
// `jvmToolchain(...)` pins the actual compiler/JDK used (Gradle provisions/selects it), rather
// than just asking whatever JDK happens to be running Gradle to emit older bytecode. The
// version itself comes from gradle/libs.versions.toml, the one place it's declared.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(libs.versions.jdk.get().toInt())
        }
    }
}

// kt2lua's own publishable modules share one group/version, declared once here instead of once
// per module.
configure(listOf(project(":common"), project(":transpiler"), project(":interpreter"), project(":gradle-plugin"))) {
    group = "kt2lua"
    version = "0.1.0"
}

// The three independently-published `runescape-*` API binding modules share everything about
// how they're published — only their artifact name/description differ, both derived from
// `project.name` ("core"/"extended"/"prettyui") below. Each module's own build.gradle.kts still
// owns its `plugins {}`/`dependencies {}` (that part *does* differ — e.g. `extended`/`prettyui`
// depend on `core`).
//
// `libs/runescape` isn't tracked by this repo's own git history (see .gitignore) — kept on disk
// for now, slated to move to its own repo eventually — so `settings.gradle.kts` only includes
// those projects when the directory actually exists. `project(":libs:runescape:core")` below
// would throw (not just produce an empty list) if the project was never included at all, so
// this whole block has to be skipped the same way, not just left to resolve an empty list.
if (file("libs/runescape").exists()) {
    val runescapeLibs = listOf(
        project(":libs:runescape:core"),
        project(":libs:runescape:extended"),
        project(":libs:runescape:prettyui"),
    )
    configure(runescapeLibs) {
        group = "com.fluxious.runescape"
        version = "0.1.0"

        plugins.withId("maven-publish") {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
            }
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        artifactId = "runescape-${project.name}"
                        from(components["java"])
                        pom {
                            name.set("runescape-${project.name}")
                            description.set(
                                "Real Kotlin RS3 API bindings — the ${project.name} module, " +
                                    "generated from rs3-api-ts. See the kt2lua repo's " +
                                    "libs/runescape/README.md for how.",
                            )
                            url.set("https://github.com/Mark7625/rs3-kt-api")
                            licenses {
                                license {
                                    name.set("MIT License")
                                    url.set("https://github.com/Mark7625/rs3-kt-api/blob/master/LICENSE")
                                }
                            }
                            developers {
                                developer {
                                    id.set("Mark7625")
                                    name.set("Mark7625")
                                    email.set("info@fluxious-rsps.com")
                                }
                            }
                            scm {
                                connection.set("scm:git:git://github.com/Mark7625/rs3-kt-api.git")
                                developerConnection.set("scm:git:ssh://github.com/Mark7625/rs3-kt-api.git")
                                url.set("https://github.com/Mark7625/rs3-kt-api")
                            }
                        }
                    }
                }
                // No `repositories {}` here on purpose — `publishToMavenLocal` already works for
                // trying a published artifact out from another local project. Add a real
                // destination (Maven Central, GitHub Packages, ...) here once there is one; see
                // libs/runescape/README.md.
            }
        }
    }
}
