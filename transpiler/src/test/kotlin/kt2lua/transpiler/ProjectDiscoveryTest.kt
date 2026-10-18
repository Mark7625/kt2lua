package kt2lua.transpiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectDiscoveryTest {

    @Test
    fun `root package and lowercase filenames also update dependency paths`() {
        val files = listOf(
            projectFileForSource("ModuleWindow", "package plugin.modules\nfun value() = 42"),
            projectFileForSource("Main", "package plugin\nfun main() { println(value()) }")
        ).map { it.withLuaLayout("plugin", lowercaseFiles = true) }
        val result = transpileProject(files)
        assertEquals(setOf("modules/modulewindow", "main"), result.files.keys)
        assertEquals(true, result.files.getValue("main").contains("require(\"modules/modulewindow\")"))
        assertEquals("com/mark/runescape/extended/pluginmodule",
            projectFileForSource("PluginModule", "package com.mark.runescape.extended\nclass PluginModule")
                .withLuaLayout("plugin", lowercaseFiles = true).name)
        assertEquals("plugins/main", ProjectFile("plugins/Main", "").withLuaLayout("plugin", true).name)
    }

    @Test
    fun `lowercase output collisions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            transpileProject(listOf(ProjectFile("Main", "fun a() {}"), ProjectFile("main", "fun b() {}"))
                .map { it.withLuaLayout(lowercaseFiles = true) })
        }
    }

    @Test
    fun `explicit file inputs use their declared package paths`() {
        val entry = projectFileForSource("Main", "package plugin\nfun main() { println(value()) }")
        val module = projectFileForSource("ModuleWindow", "package plugin.modules\nfun value() = 42")
        val result = transpileProject(listOf(module, entry))
        assertEquals(setOf("plugin/modules/ModuleWindow", "plugin/Main"), result.files.keys)
        assertEquals(true, result.files["plugin/Main"]!!.contains("require(\"plugin/modules/ModuleWindow\")"))
    }

    @Test
    fun `package declarations override mismatched source folders`() {
        val root = Files.createTempDirectory("kt2lua-packages-test").toFile()
        root.resolve("elsewhere").mkdirs()
        root.resolve("elsewhere/Window.kt").writeText("package plugin.modules\nfun window() {}")
        assertEquals(listOf("plugin/modules/Window"), discoverProjectFiles(root).map { it.name })
    }

    @Test
    fun `duplicate output paths fail instead of silently overwriting files`() {
        assertFailsWith<IllegalArgumentException> {
            transpileProject(listOf(ProjectFile("same", "fun a() {}"), ProjectFile("same", "fun b() {}")))
        }
    }

    @Test
    fun `nested files are named by their path relative to root`() {
        val root = Files.createTempDirectory("kt2lua-discovery-test").toFile()
        root.resolve("Main.kt").writeText("fun main() {}")
        root.resolve("pkg").mkdirs()
        root.resolve("pkg/Thing.kt").writeText("fun thing() {}")
        root.resolve("pkg/sub").mkdirs()
        root.resolve("pkg/sub/Deep.kt").writeText("fun deep() {}")
        root.resolve("ignored.txt").writeText("not kotlin")

        val files = discoverProjectFiles(root)

        assertEquals(listOf("Main", "pkg/Thing", "pkg/sub/Deep"), files.map { it.name })
    }

    @Test
    fun `require paths for nested files resolve through the directory layout`() {
        val root = Files.createTempDirectory("kt2lua-discovery-test").toFile()
        root.resolve("Main.kt").writeText(
            """
            fun main() {
                println(thing())
            }
            """.trimIndent()
        )
        root.resolve("pkg").mkdirs()
        root.resolve("pkg/Thing.kt").writeText("fun thing() = 1")

        val files = discoverProjectFiles(root)
        val result = transpileProject(files)

        assertEquals(setOf("Main", "pkg/Thing"), result.files.keys)
        assertEquals(true, result.files["Main"]!!.contains("require(\"pkg/Thing\")"))
    }
}
