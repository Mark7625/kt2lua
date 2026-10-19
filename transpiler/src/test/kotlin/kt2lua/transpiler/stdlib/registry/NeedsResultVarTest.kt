package kt2lua.transpiler.stdlib.registry

import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertTrue

class NeedsResultVarTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a value-producing call sharing a registry slot with a mutation still gets a result`() {
        assertTrue(emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.map { it * 2 } }").contains("local ys = {}"))
        assertTrue(emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.filter { it > 1 } }").contains("local ys = {}"))
        assertTrue(emit("fun main() { val xs = listOf(1, 2, 3); val total = xs.fold(0) { acc, x -> acc + x } }").contains("local acc = 0"))
        assertTrue(emit("fun main() { val xs = listOf(1, 2, 3); val total = xs.reduce { acc, x -> acc + x } }").contains("local acc = xs[1]"))
    }

    @Test
    fun `a value-producing call used as a discard statement does not crash`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); xs.map { println(it) } }")
        assertTrue(code.contains("ipairs(xs)"))
    }

    @Test
    fun `a pure mutation sharing the same registry still runs with no resultVar`() {
        val code = emit("fun main() { val xs = mutableListOf(1, 2, 3); xs.add(4) }")
        assertTrue(code.contains("table.insert(xs, 4)"))
    }
}
