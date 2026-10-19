package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupingOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `groupBy collects every element under its key`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3, 4); val m = xs.groupBy { it % 2 } }")
        assertTrue(code.contains("local m = {keys = {}, map = {}}"))
        assertTrue(code.contains("table.insert(m.map[__k], it)"))
    }

    @Test
    fun `associate folds pair results into a Map`() {
        val code = emit("fun main() { val xs = listOf(1, 2); val m = xs.associate { it to it * it } }")
        assertTrue(code.contains("local __p ="))
        assertTrue(code.contains("m.map[__p.first] = __p.second"))
    }

    @Test
    fun `associateBy keys by the selector, values are the original elements`() {
        val code = emit("fun main() { val xs = listOf(1, 2); val m = xs.associateBy { it * 10 } }")
        assertTrue(code.contains("m.map[__k] = it"))
    }

    @Test
    fun `associateWith keys by the element, values from the selector`() {
        val code = emit("fun main() { val xs = listOf(1, 2); val m = xs.associateWith { it * 10 } }")
        assertTrue(code.contains("m.map[it] ="))
    }

    @Test
    fun `works for a Set receiver too`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); val m = xs.groupBy { it % 2 } }")
        assertTrue(code.contains("ipairs(xs.list)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(GroupByOperation(), AssociateOperation(), AssociateByOperation(), AssociateWithOperation())) {
            assertEquals(CompatibilityStatus.PARTIAL, registry.status(op.id), "no 'partial' manifest entry for ${op.id}")
        }
    }
}
