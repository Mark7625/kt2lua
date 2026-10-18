import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
}

val sharedJdkVersion = libs.versions.jdk.get().toInt()
subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(sharedJdkVersion))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(sharedJdkVersion)
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(sharedJdkVersion)
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(sharedJdkVersion.toString()))
        }
    }
}

configure(listOf(project(":cli"), project(":all"))) {
    val versionDir = layout.buildDirectory.dir("generated/version")
    val generateVersion = tasks.register("generateVersion") {
        val projectVersion = project.version.toString()
        val outputFile = versionDir.map { it.file("kt2lua-version.properties") }
        inputs.property("version", projectVersion)
        outputs.dir(versionDir)
        doLast {
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText("version=$projectVersion\n")
            }
        }
    }
    plugins.withId("java") {
        extensions.getByType<SourceSetContainer>()["main"].resources.srcDir(generateVersion)
    }
}

project(":all") {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            coordinates("io.github.mark7625", "kt2lua", version.toString())
            pom {
                name.set("kt2lua")
                description.set("Transpiles a subset of Kotlin to Lua 5.1: transpiler, interpreter, CLI and Gradle plugin in one artifact.")
                inceptionYear.set("2026")
                url.set("https://github.com/Mark7625/kt2lua")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/Mark7625/kt2lua/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("Mark7625")
                        name.set("Mark7625")
                        url.set("https://github.com/Mark7625")
                    }
                }
                scm {
                    url.set("https://github.com/Mark7625/kt2lua")
                    connection.set("scm:git:git://github.com/Mark7625/kt2lua.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Mark7625/kt2lua.git")
                }
            }
        }
    }
}
