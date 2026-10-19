package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TakeDropOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `take and drop work for both List and Set`() {
        val listCode = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.take(2); val b = xs.drop(2) }")
        assertTrue(listCode.contains("math.min((2), #xs)"))
        assertTrue(listCode.contains("(2) + 1, #xs"))

        val setCode = emit("fun main() { val xs = setOf(1, 2, 3); val a = xs.take(2) }")
        assertTrue(setCode.contains("#xs.list"))
    }

    @Test
    fun `takeLast and dropLast index from the end`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.takeLast(2); val b = xs.dropLast(1) }")
        assertTrue(code.contains("math.max(1, #xs - (2) + 1), #xs"))
        assertTrue(code.contains("1, #xs - (1)"))
    }

    @Test
    fun `takeWhile and dropWhile use a predicate`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val a = xs.takeWhile { it < 3 }; val b = xs.dropWhile { it < 3 } }")
        assertTrue(code.contains("if not ((it < 3)) then break end"))
        assertTrue(code.contains("__dropping"))
    }

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val ys = oop_lib.take(2) }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.take(2)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(TakeOperation(), DropOperation(), TakeLastOperation(), DropLastOperation(), TakeWhileOperation(), DropWhileOperation())) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
