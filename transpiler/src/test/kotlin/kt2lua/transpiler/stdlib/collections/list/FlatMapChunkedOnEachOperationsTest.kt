package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlatMapChunkedOnEachOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `flatMap concatenates every mapped sub-collection`() {
        val code = emit("fun main() { val xs = listOf(1, 2); val ys = xs.flatMap { listOf(it, it) } }")
        assertTrue(code.contains("for _, __e in ipairs"))
    }

    @Test
    fun `chunked groups into fixed-size sub-lists`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.chunked(2) }")
        assertTrue(code.contains("(__i - 1) % (2) == 0"))
    }

    @Test
    fun `onEach runs the side effect and evaluates to the receiver, hoisted exactly once`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.onEach { println(it) } }")
        assertTrue(code.contains("for _, it in ipairs(xs) do"))
        assertTrue(code.contains("local ys = xs"))
    }

    @Test
    fun `onEach hoists a side-effecting receiver only once`() {
        val code = emit("""
            fun makeList(): List<Int> = listOf(1, 2)
            fun main() { val ys = makeList().onEach { println(it) } }
        """.trimIndent())
        val callCount = Regex("""(?<!function )makeList\(\)""").findAll(code).count()
        assertTrue(callCount == 1, "expected makeList() to be called exactly once, got:\n$code")
    }

    @Test
    fun `onEach works for a Set receiver too`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); val ys = xs.onEach { println(it) } }")
        assertTrue(code.contains("ipairs(xs.list)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(FlatMapOperation(), ChunkedOperation(), OnEachOperation())) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
