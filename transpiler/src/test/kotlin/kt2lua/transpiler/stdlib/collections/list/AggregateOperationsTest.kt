package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggregateOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `any, all and none short-circuit on the first match`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.any { it > 2 }; val b = xs.all { it > 0 }; val c = xs.none { it > 5 } }")
        assertTrue(code.contains("a = true; break"))
        assertTrue(code.contains("b = false; break"))
        assertTrue(code.contains("c = false; break"))
    }

    @Test
    fun `count works with and without a predicate`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.count(); val b = xs.count { it > 1 } }")
        assertTrue(code.contains("local a = #xs"))
        assertTrue(code.contains("local b = 0"))
    }

    @Test
    fun `sum and sumOf accumulate correctly`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.sum(); val b = xs.sumOf { it * it } }")
        assertTrue(code.contains("for _, __v in ipairs(xs) do a = a + __v end"))
        assertTrue(code.contains("b = b + ((it * it))"))
    }

    @Test
    fun `work for a Set receiver too`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); val a = xs.sum() }")
        assertTrue(code.contains("ipairs(xs.list)"))
    }

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val a = oop_lib.sum() }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.sum()"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(AnyAllNoneOperation("any"), AnyAllNoneOperation("all"), AnyAllNoneOperation("none"), CountOperation())) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(SumOperation("sum", hasSelector = false).id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(SumOperation("sumOf", hasSelector = true).id))
    }
}
