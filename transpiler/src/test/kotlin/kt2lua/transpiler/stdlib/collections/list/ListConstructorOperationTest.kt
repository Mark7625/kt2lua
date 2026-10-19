package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListConstructorOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `listOf, mutableListOf and arrayListOf all compile to the same bare table literal`() {
        for (callee in listOf("listOf", "mutableListOf", "arrayListOf")) {
            val code = emit("fun main() { val xs = $callee(1, 2, 3) }")
            assertTrue(code.contains("local xs = {1, 2, 3}"), "expected a bare table literal for $callee, got:\n$code")
        }
    }

    @Test
    fun `emptyList compiles to an empty table literal`() {
        val code = emit("fun main() { val xs = emptyList<Int>() }")
        assertTrue(code.contains("local xs = {}"))
    }

    @Test
    fun `a spread argument builds the fixed prefix then appends the spread source with a loop`() {
        val code = emit("fun main() { val ys = listOf(1, *xs) }")
        assertTrue(code.contains("local ys = {1}"))
        assertTrue(code.contains("for _, __e in ipairs(xs) do table.insert(ys, __e) end"))
    }

    @Test
    fun `used as a nested expression argument, still just the bare literal (no IIFE needed)`() {
        val code = emit("fun main() { println(foo(listOf(1, 2))) } fun foo(xs: List<Int>): Int = xs.size")
        assertTrue(code.contains("foo({1, 2})"))
    }

    @Test
    fun `a listOf result still tracks as a List for later operations like forEach`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); xs.forEach { println(it) } }")
        assertTrue(code.contains("for _, it in ipairs(xs) do"))
    }

    @Test
    fun `this operation's ids for all four spellings match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val withTParam = listOf("listOf", "mutableListOf", "arrayListOf")
            .map { ListConstructorOperation(it, parameters = listOf("T")) }
        val noParam = ListConstructorOperation("emptyList", parameters = emptyList())
        for (op in withTParam + noParam) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
