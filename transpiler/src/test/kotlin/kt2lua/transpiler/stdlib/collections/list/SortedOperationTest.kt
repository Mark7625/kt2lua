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
