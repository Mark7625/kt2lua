package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapForEachOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `forEach with a two-param lambda destructures key and value`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1); m.forEach { (k, v) -> println(k); println(v) } }""")
        assertTrue(code.contains("for _, k in ipairs(m.keys) do"))
        assertTrue(code.contains("local v = m.map[k]"))
    }

    @Test
    fun `forEach with a single entry-param lambda uses key value fields`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1); m.forEach { println(it.key); println(it.value) } }""")
        assertTrue(code.contains("local it = {key = __k, value = m.map[__k]}"))
    }

    @Test
    fun `this operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(MapForEachOperation().id))
    }
}
