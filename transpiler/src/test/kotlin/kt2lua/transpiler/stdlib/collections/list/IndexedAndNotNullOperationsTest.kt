package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexedAndNotNullOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `mapIndexed and filterIndexed derive a 0-based index from ipairs`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.mapIndexed { i, v -> i + v }; val b = xs.filterIndexed { i, v -> i > 0 } }")
        assertTrue(code.contains("local i = __i - 1"))
    }

    @Test
    fun `forEachIndexed is a plain statement, dispatched separately from the gated tier`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); xs.forEachIndexed { i, v -> println(i) } }")
        assertTrue(code.contains("local i = __i - 1"))
        assertTrue(!code.contains("local __discard"))
    }

    @Test
    fun `mapNotNull drops nulls, filterNotNull drops null elements`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.mapNotNull { if (it > 1) it else null }; val b = xs.filterNotNull() }")
        assertTrue(code.contains("local __r ="))
        assertTrue(code.contains("if __v ~= nil then table.insert(b, __v) end"))
    }

    @Test
    fun `work for a Set receiver too`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); val a = xs.mapIndexed { i, v -> v } }")
        assertTrue(code.contains("ipairs(xs.list)"))
    }

    @Test
    fun `a module's own method of the same name is not shadowed for the gated tier`() {
        val code = emit("""
            import oop_lib
            fun main() { val a = oop_lib.mapNotNull(1) }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.mapNotNull(1)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(MapIndexedOperation(), FilterIndexedOperation(), ForEachIndexedOperation(), MapNotNullOperation(), FilterNotNullOperation())) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
