package kt2lua.testrunner

import kt2lua.testrunner.differential.DifferentialResult
import kt2lua.testrunner.differential.LuaFixtureExecutor
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stdlib functions newer than the Kotlin compiler the differential suite runs, so there is nothing to
 * diff against: the expected output here is written by hand from the documented behaviour. */
class NewStdlibApiTest {
    private fun output(source: String): String {
        val run = LuaFixtureExecutor.run(source)
        val result = run.result
        check(result is DifferentialResult.Success) { "${result.describe()}\n${run.generatedLua}" }
        return result.output
    }

    @Test
    fun `AbstractDoubleTimeSource reads its unit and marks measure against it`() {
        val out = output(
            """
            import kotlin.time.AbstractDoubleTimeSource
            import kotlin.time.DurationUnit

            class HalfSeconds : AbstractDoubleTimeSource(DurationUnit.SECONDS) {
                var ticks = 0.0
                override fun read(): Double = ticks / 2
            }

            fun main() {
                val clock = HalfSeconds()
                val mark = clock.markNow()
                clock.ticks = 5.0
                println(mark.elapsedNow())
                clock.ticks = 7.0
                println(mark.elapsedNow().inWholeMilliseconds)
            }
            """.trimIndent(),
        )
        assertEquals("2.5s\n3500\n", out)
    }

    @Test
    fun `toJSDate keeps the Instant as it is`() {
        val out = output(
            """
            import kotlin.time.Instant
            import kotlin.time.toJSDate

            fun main() {
                val t = Instant.fromEpochSeconds(86400)
                println(t.toJSDate() == t)
            }
            """.trimIndent(),
        )
        assertEquals("true\n", out)
    }

    @Test
    fun `sequence allDistinct, allEqual and isSorted families`() {
        val out = output(
            """
            fun main() {
                val xs = sequenceOf(3, 1, 4)
                println(xs.allDistinct())
                println(sequenceOf(1, 1).allDistinct())
                println(xs.allDistinctBy { it % 2 })
                println(xs.allDistinctBy { it })
                println(sequenceOf(2, 2).allEqual())
                println(xs.allEqual())
                println(xs.allEqualBy { it > 0 })
                println(emptySequence<Int>().allEqual())
                println(sequenceOf(1, 2, 2, 3).isSorted())
                println(xs.isSorted())
                println(sequenceOf(3, 2, 1).isSortedDescending())
                println(xs.isSortedDescending())
                println(sequenceOf(1, 2, 3).isSortedBy { -it })
                println(sequenceOf(3, 2, 1).isSortedBy { -it })
                println(sequenceOf(3, 2, 1).isSortedByDescending { it })
                println(xs.isSortedByDescending { it })
                println(sequenceOf(1, 2, 3).isSortedWith(compareBy { it }))
                println(xs.isSortedWith(compareBy { it }))
                println(emptySequence<Int>().isSorted())
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "true", "false", "false", "true", "true", "false", "true", "true",
                "true", "false", "true", "false", "false", "true", "true", "false", "true", "false", "true",
            ).joinToString("\n", postfix = "\n"),
            out,
        )
    }
}
