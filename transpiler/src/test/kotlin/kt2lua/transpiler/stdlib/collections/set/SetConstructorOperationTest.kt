package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetConstructorOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a spread argument loops over the spread source with the same dedup check`() {
        val code = emit("fun main() { val ys = setOf(1, *xs) }")
        assertTrue(code.contains("for _, __e in ipairs(xs) do"))
        assertTrue(code.contains("table.insert(ys.list, __e)"))
    }

    @Test
    fun `this operation's ids for all spellings match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val withTParam = listOf("setOf", "mutableSetOf")
            .map { SetConstructorOperation(it, parameters = listOf("T")) }
        for (op in withTParam) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        val hashSetOf = SetConstructorOperation("hashSetOf", parameters = listOf("T"))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(hashSetOf.id), "no 'partial' manifest entry for ${hashSetOf.id}")
        val emptySet = SetConstructorOperation("emptySet", parameters = emptyList())
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(emptySet.id), "no 'supported' manifest entry for ${emptySet.id}")
    }
}
