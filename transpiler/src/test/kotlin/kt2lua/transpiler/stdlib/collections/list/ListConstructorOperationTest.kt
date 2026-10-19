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
    fun `a spread argument builds the fixed prefix then appends the spread source with a loop`() {
        val code = emit("fun main() { val ys = listOf(1, *xs) }")
        assertTrue(code.contains("local ys = {__nilEncode(1)}"))
        assertTrue(code.contains("for _, __e in ipairs(xs) do table.insert(ys, __e) end"))
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
