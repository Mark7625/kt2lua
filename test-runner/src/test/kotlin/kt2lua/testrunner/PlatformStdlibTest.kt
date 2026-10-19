package kt2lua.testrunner

import kt2lua.interpreter.interpret
import kt2lua.testrunner.differential.DifferentialResult
import kt2lua.testrunner.differential.LuaFixtureExecutor
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** kotlin.system functions the differential suite can't run in-process (exiting would end the test JVM)
 * or that only exist on Kotlin/Native (getTimeMillis), so they are checked against the interpreter directly. */
class PlatformStdlibTest {
    private fun output(source: String): String {
        val run = LuaFixtureExecutor.run(source)
        val result = run.result
        check(result is DifferentialResult.Success) { "${result.describe()}\n${run.generatedLua}" }
        return result.output
    }

    @Test
    fun `exitProcess stops the script and nothing after it runs`() {
        assertEquals("before\n", output("import kotlin.system.exitProcess\nfun main() { println(\"before\"); exitProcess(0); println(\"after\") }"))
    }

    @Test
    fun `exitProcess hands its code back from interpret`() {
        val code = interpret(transpile("import kotlin.system.exitProcess\nfun main() { exitProcess(3) }").code, onOutput = {})
        assertEquals(3, code)
    }

    @Test
    fun `a script that never exits reports exit code 0`() {
        assertEquals(0, interpret(transpile("fun main() { println(1) }").code, onOutput = {}))
    }

    @Test
    fun `wall clock readings are positive whole numbers`() {
        val out = output(
            """
            import kotlin.system.getTimeMillis
            import kotlin.system.getTimeMicros
            import kotlin.system.getTimeNanos
            fun main() {
                println(getTimeMillis() > 1000000000000)
                println(getTimeMicros() > getTimeMillis())
                println(getTimeNanos() > getTimeMicros())
            }
            """.trimIndent(),
        )
        assertEquals("true\ntrue\ntrue\n", out)
    }

    @Test
    fun `measureTimeMicros times the block and still runs it`() {
        val out = output(
            """
            import kotlin.system.measureTimeMicros
            fun main() {
                var ran = 0
                val t = measureTimeMicros { ran += 5 }
                println(ran)
                println(t >= 0)
            }
            """.trimIndent(),
        )
        assertEquals("5\ntrue\n", out)
    }

    @Test
    fun `a user function with a system name is left alone`() {
        assertTrue(transpile("fun getTimeMillis(): Int = 7\nfun main() { println(getTimeMillis()) }").code.contains("getTimeMillis()"))
    }
}
