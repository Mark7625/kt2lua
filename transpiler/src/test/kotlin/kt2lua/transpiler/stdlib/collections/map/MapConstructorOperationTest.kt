package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapConstructorOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a spread argument loops over the spread source of Pairs`() {
        val code = emit("fun main() { val m = mapOf(*ps) }")
        assertTrue(code.contains("for _, __p in ipairs(ps) do"))
        assertTrue(code.contains("__p.first"))
        assertTrue(code.contains("__p.second"))
    }

    @Test
    fun `this operation's ids for all spellings match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val withPairParam = listOf("mapOf", "mutableMapOf")
            .map { MapConstructorOperation(it, parameters = listOf("Pair<K, V>")) }
        for (op in withPairParam) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        val hashMapOf = MapConstructorOperation("hashMapOf", parameters = listOf("Pair<K, V>"))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(hashMapOf.id), "no 'partial' manifest entry for ${hashMapOf.id}")
        val emptyMap = MapConstructorOperation("emptyMap", parameters = emptyList())
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(emptyMap.id), "no 'supported' manifest entry for ${emptyMap.id}")
    }
}
