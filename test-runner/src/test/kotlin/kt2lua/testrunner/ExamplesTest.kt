package kt2lua.testrunner

import kt2lua.interpreter.DirectoryModuleLoader
import kt2lua.interpreter.interpret
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

/**
 * Drives the `kt2lua.transpile` Gradle task's output: for every sample `.kt` script under
 * src/test/resources/examples, the plugin has already produced a `.lua` file under
 * build/generated/kt2lua (wired via the `transpileKotlinToLua` task dependency on `test`).
 * This runs that generated Lua with kt2lua's own interpreter and checks the captured output
 * against the matching `.expected.txt` file.
 */
class ExamplesTest {

    private val examplesDir = File("src/test/resources/examples")
    private val generatedDir = File("build/generated/kt2lua")

    @TestFactory
    fun runExamples(): List<DynamicTest> {
        val ktFiles = examplesDir.listFiles { f -> f.extension == "kt" }
            ?.sortedBy { it.name }
            ?: emptyList()
        check(ktFiles.isNotEmpty()) { "No example .kt files found in $examplesDir" }

        return ktFiles.map { ktFile ->
            val name = ktFile.nameWithoutExtension
            dynamicTest(name) {
                val luaFile = File(generatedDir, "$name.lua")
                check(luaFile.exists()) { "Generated Lua not found: $luaFile (did transpileKotlinToLua run?)" }
                val expectedFile = File(examplesDir, "$name.expected.txt")
                check(expectedFile.exists()) { "Missing expected output file: $expectedFile" }

                val expected = expectedFile.readText().trim().replace("\r\n", "\n")
                val actual = runLua(luaFile).trim().replace("\r\n", "\n")
                assertEquals(expected, actual, "Output mismatch for ${ktFile.name}")
            }
        }
    }

    private fun runLua(luaFile: File): String {
        val output = StringBuilder()
        interpret(luaFile.readText(), DirectoryModuleLoader(generatedDir)) { output.append(it) }
        return output.toString()
    }
}
