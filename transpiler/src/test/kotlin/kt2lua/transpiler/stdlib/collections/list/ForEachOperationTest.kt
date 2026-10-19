package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForEachOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `forEach inlines to a plain for loop for a List receiver`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); xs.forEach { println(it) } }")
        assertTrue(code.contains("for _, it in ipairs(xs) do"))
    }

    @Test
    fun `forEach iterates the underlying list for a Set receiver`() {
        val code = emit("fun main() { val xs = setOf(1, 2, 3); xs.forEach { println(it) } }")
        assertTrue(code.contains("for _, it in ipairs(xs.list) do"))
    }

    @Test
    fun `used as a nested expression, still a statement wrapped in an IIFE`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); println(xs.also { it.forEach { x -> println(x) } }) }")
        assertTrue(code.contains("for _, x in ipairs"))
    }

    @Test
    fun `this operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(CollectionForEachOperation().id))
    }
}
