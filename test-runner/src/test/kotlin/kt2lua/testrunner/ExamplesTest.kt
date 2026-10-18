package kt2lua.testrunner

import kt2lua.interpreter.DirectoryModuleLoader
import kt2lua.interpreter.interpret
import kt2lua.transpiler.EmitStyle
import kt2lua.transpiler.discoverProjectFiles
import kt2lua.transpiler.transpileProject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

/**
 * Transpiles every sample `.kt` script under src/test/resources/examples (together, so
 * cross-file references resolve), runs the result with kt2lua's own interpreter, and checks
 * the captured output against the matching `.expected.txt` file.
 */
class ExamplesTest {

    private val examplesDir = File("src/test/resources/examples")
    private val generatedDir = File("build/generated/kt2lua")

    @TestFactory
    fun runExamples(): List<DynamicTest> {
        val projectFiles = discoverProjectFiles(examplesDir)
        check(projectFiles.isNotEmpty()) { "No example .kt files found in $examplesDir" }

        val result = transpileProject(projectFiles, EmitStyle.DEFAULT)
        generatedDir.mkdirs()
        for ((name, code) in result.files) {
            generatedDir.resolve("$name.lua").writeText(code)
        }

        return projectFiles.map { file ->
            val name = file.name
            dynamicTest(name) {
                val luaFile = File(generatedDir, "$name.lua")
                val expectedFile = File(examplesDir, "$name.expected.txt")
                check(expectedFile.exists()) { "Missing expected output file: $expectedFile" }

                val expected = expectedFile.readText().trim().replace("\r\n", "\n")
                val actual = runLua(luaFile).trim().replace("\r\n", "\n")
                assertEquals(expected, actual, "Output mismatch for $name.kt")
            }
        }
    }

    private fun runLua(luaFile: File): String {
        val output = StringBuilder()
        interpret(luaFile.readText(), DirectoryModuleLoader(generatedDir)) { output.append(it) }
        return output.toString()
    }
}
