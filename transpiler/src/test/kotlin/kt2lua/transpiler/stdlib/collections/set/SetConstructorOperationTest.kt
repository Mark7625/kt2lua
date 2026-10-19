package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetConstructorOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `setOf, mutableSetOf and hashSetOf all build the same list-plus-has representation`() {
        for (callee in listOf("setOf", "mutableSetOf", "hashSetOf")) {
            val code = emit("fun main() { val xs = $callee(1, 2, 3) }")
            assertTrue(code.contains("local xs = {list = {}, has = {}}"), "expected Set construction for $callee, got:\n$code")
            assertTrue(code.contains("table.insert(xs.list, 1)"), "expected dedup-tracked inserts for $callee, got:\n$code")
        }
    }

    @Test
    fun `emptySet compiles to an empty Set representation`() {
        val code = emit("fun main() { val xs = emptySet<Int>() }")
        assertTrue(code.contains("local xs = {list = {}, has = {}}"))
    }

    @Test
    fun `a spread argument loops over the spread source with the same dedup check`() {
        val code = emit("fun main() { val ys = setOf(1, *xs) }")
        assertTrue(code.contains("for _, __e in ipairs(xs) do"))
        assertTrue(code.contains("table.insert(ys.list, __e)"))
    }

    @Test
    fun `used as a nested expression argument, built via an IIFE`() {
        val code = emit("fun main() { println(foo(setOf(1, 2))) } fun foo(xs: Set<Int>): Int = xs.list.size")
        assertTrue(code.contains("(function()"))
        assertTrue(code.contains("list = {}, has = {}"))
    }

    @Test
    fun `this operation's ids for all spellings match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val withTParam = listOf("setOf", "mutableSetOf")
            .map { SetConstructorOperation(it, parameters = listOf("T")) }
        for (op in withTParam) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        val hashSetOf = SetConstructorOperation("hashSetOf", parameters = listOf("T"))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(hashSetOf.id), "no 'partial' manifest entry for ${hashSetOf.id}")
        val emptySet = SetConstructorOperation("emptySet", parameters = emptyList())
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(emptySet.id), "no 'supported' manifest entry for ${emptySet.id}")
    }
}
