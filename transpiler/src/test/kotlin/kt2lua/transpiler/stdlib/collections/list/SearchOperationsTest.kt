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
    fun `first, find and firstOrNull scan forward`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.first { it > 1 }; val b = xs.find { it > 1 }; val c = xs.firstOrNull { it > 1 } }")
        assertTrue(code.contains("for __i = 1, #xs do"))
    }

    @Test
    fun `last and lastOrNull scan backward`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.last { it < 3 }; val b = xs.lastOrNull { it < 3 } }")
        assertTrue(code.contains("for __i = #xs, 1, -1 do"))
    }

    @Test
    fun `indexOf finds by structural equality`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val i = xs.indexOf(2) }")
        assertTrue(code.contains("if (__v == 2) then i = __i - 1; break end"))
    }

    @Test
    fun `indexOfFirst and indexOfLast scan by predicate`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.indexOfFirst { it > 1 }; val b = xs.indexOfLast { it > 1 } }")
        assertTrue(code.contains("for __i = 1, #xs do"))
        assertTrue(code.contains("for __i = #xs, 1, -1 do"))
    }

    @Test
    fun `work for a Set receiver too`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); val a = xs.first { it > 1 } }")
        assertTrue(code.contains("#xs.list"))
    }

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
