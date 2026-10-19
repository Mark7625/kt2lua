package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapMapFilterOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `map builds a plain List of the transformed entries`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1, "b" to 2); val xs = m.map { (k, v) -> v * 2 } }""")
        assertTrue(code.contains("local xs = {}"))
        assertTrue(code.contains("for __i, k in ipairs(m.keys) do"))
        assertTrue(code.contains("xs[__i] = (v * 2)"))
    }

    @Test
    fun `filter builds a new filtered Map`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1, "b" to -1); val m2 = m.filter { (k, v) -> v > 0 } }""")
        assertTrue(code.contains("local m2 = {keys = {}, map = {}}"))
        assertTrue(code.contains("if (v > 0) then"))
    }

    @Test
    fun `single-param entry lambda works for both map and filter`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1); val xs = m.map { it.value } }""")
        assertTrue(code.contains("local it = {key = __k, value = m.map[__k]}"))
    }

    @Test
    fun `this operation's ids match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(MapMapOperation().id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(MapFilterOperation().id))
    }
}
