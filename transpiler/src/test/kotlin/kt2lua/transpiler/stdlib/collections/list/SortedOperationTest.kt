package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SortedOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `sorted and sortedDescending produce a fresh list, leaving the receiver untouched`() {
        val code = emit("fun main() { val xs = listOf(3, 1, 2); val ys = xs.sorted(); val zs = xs.sortedDescending() }")
        assertTrue(code.contains("local ys = {}"))
        assertTrue(code.contains("local zs = {}"))
        assertTrue(code.contains("table.sort("))
    }

    @Test
    fun `sortedBy and sortedByDescending work for both List and Set`() {
        val listCode = emit("fun main() { val xs = listOf(3, 1, 2); val ys = xs.sortedBy { -it } }")
        assertTrue(listCode.contains("k = -it") || listCode.contains("k = (-it)"))

        val setCode = emit("fun main() { val xs = setOf(3, 1, 2); val ys = xs.sortedByDescending { it } }")
        assertTrue(setCode.contains("ipairs(xs.list)"), "expected Set.sortedByDescending to iterate xs.list, got:\n$setCode")
    }

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val ys = oop_lib.sorted() }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.sorted()"), "expected the module's own .sorted() dot-call to survive untouched, got:\n$code")
    }

    @Test
    fun `every spelling's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val ops = listOf(
            SortedOperation("sorted", hasSelector = false, descending = false),
            SortedOperation("sortedDescending", hasSelector = false, descending = true),
            SortedOperation("sortedBy", hasSelector = true, descending = false),
            SortedOperation("sortedByDescending", hasSelector = true, descending = true),
        )
        for (op in ops) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
