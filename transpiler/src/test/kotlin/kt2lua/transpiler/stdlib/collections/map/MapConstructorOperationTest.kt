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
    fun `mapOf, mutableMapOf and hashMapOf all build the same keys-plus-map representation`() {
        for (callee in listOf("mapOf", "mutableMapOf", "hashMapOf")) {
            val code = emit("""fun main() { val m = $callee("a" to 1, "b" to 2) }""")
            assertTrue(code.contains("local m = {keys = {}, map = {}}"), "expected Map construction for $callee, got:\n$code")
            assertTrue(code.contains("m.map[\"a\"] = 1"), "expected put-style assignment for $callee, got:\n$code")
        }
    }

    @Test
    fun `emptyMap compiles to an empty Map representation`() {
        val code = emit("fun main() { val m = emptyMap<String, Int>() }")
        assertTrue(code.contains("local m = {keys = {}, map = {}}"))
    }

    @Test
    fun `a spread argument loops over the spread source of Pairs`() {
        val code = emit("fun main() { val m = mapOf(*ps) }")
        assertTrue(code.contains("for _, __p in ipairs(ps) do"))
        assertTrue(code.contains("__p.first"))
        assertTrue(code.contains("__p.second"))
    }

    @Test
    fun `used as a nested expression argument, built via an IIFE`() {
        val code = emit("""fun main() { println(foo(mapOf("a" to 1))) } fun foo(m: Map<String, Int>): Int = m.map["a"]""")
        assertTrue(code.contains("(function()"))
        assertTrue(code.contains("keys = {}, map = {}"))
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
