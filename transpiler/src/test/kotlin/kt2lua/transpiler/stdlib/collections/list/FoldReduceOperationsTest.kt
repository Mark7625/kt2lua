package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldReduceOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `fold seeds an accumulator and folds left to right for both List and Set`() {
        val listCode = emit("fun main() { val xs = listOf(1, 2, 3); val total = xs.fold(0) { acc, x -> acc + x } }")
        assertTrue(listCode.contains("local acc = 0"))
        assertTrue(listCode.contains("for _, x in ipairs(xs) do"))

        val setCode = emit("fun main() { val xs = setOf(1, 2, 3); val total = xs.fold(0) { acc, x -> acc + x } }")
        assertTrue(setCode.contains("for _, x in ipairs(xs.list) do"), "expected Set.fold to iterate xs.list, got:\n$setCode")
    }

    @Test
    fun `reduce seeds from the first element`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val total = xs.reduce { acc, x -> acc + x } }")
        assertTrue(code.contains("local acc = xs[1]"))
        assertTrue(code.contains("for __i = 2, #xs do"))
    }

    @Test
    fun `used as a nested expression argument, built via an IIFE`() {
        val code = emit("fun main() { println(listOf(1, 2, 3).fold(0) { acc, x -> acc + x }) }")
        assertTrue(code.contains("(function()"))
    }

    @Test
    fun `this operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(FoldOperation().id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(ReduceOperation().id))
    }
}
