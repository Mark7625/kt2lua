package kt2lua.transpiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectDiscoveryTest {

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
