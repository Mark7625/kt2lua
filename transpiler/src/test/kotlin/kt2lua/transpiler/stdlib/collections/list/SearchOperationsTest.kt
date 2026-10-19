package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val a = oop_lib.first(2) }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.first(2)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(
            FindOperation("first", fromEnd = false), FindOperation("find", fromEnd = false), FindOperation("firstOrNull", fromEnd = false),
            FindOperation("last", fromEnd = true), FindOperation("lastOrNull", fromEnd = true),
            IndexOfPredicateOperation("indexOfFirst", fromEnd = false), IndexOfPredicateOperation("indexOfLast", fromEnd = true),
        )) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(IndexOfOperation().id))
    }
}
