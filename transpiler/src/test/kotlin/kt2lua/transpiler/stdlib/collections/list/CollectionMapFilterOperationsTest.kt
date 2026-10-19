package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionMapFilterOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `map produces a fresh list for both a List and a Set receiver`() {
        val listCode = emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.map { it * 2 } }")
        assertTrue(listCode.contains("for __i, it in ipairs(xs) do"))
        assertTrue(listCode.contains("ys[__i] = (it * 2)"))

        val setCode = emit("fun main() { val xs = setOf(1, 2, 3); val ys = xs.map { it * 2 } }")
        assertTrue(setCode.contains("for __i, it in ipairs(xs.list) do"), "expected Set.map to iterate xs.list, got:\n$setCode")
        assertTrue(setCode.contains("local ys = {}"), "Set.map must produce a plain List, not a Set, got:\n$setCode")
    }

    @Test
    fun `filter produces a fresh list for both a List and a Set receiver`() {
        val listCode = emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.filter { it > 1 } }")
        assertTrue(listCode.contains("if (it > 1) then table.insert(ys, it) end"))

        val setCode = emit("fun main() { val xs = setOf(1, 2, 3); val ys = xs.filter { it > 1 } }")
        assertTrue(setCode.contains("for _, it in ipairs(xs.list) do"), "expected Set.filter to iterate xs.list, got:\n$setCode")
    }

    @Test
    fun `map and filter on a Map use the entry-aware shape and keep their own ids`() {
        val mapCode = emit("""fun main() { val m = mapOf("a" to 1); val ys = m.map { (k, v) -> v } }""")
        assertTrue(mapCode.contains("for __i, k in ipairs(m.keys) do"))
        val filterCode = emit("""fun main() { val m = mapOf("a" to 1); val m2 = m.filter { (k, v) -> v > 0 } }""")
        assertTrue(filterCode.contains("local m2 = {keys = {}, map = {}}"))
    }

    @Test
    fun `used as a nested expression argument, built via an IIFE`() {
        val code = emit("fun main() { println(listOf(1, 2).map { it + 1 }.size) }")
        assertTrue(code.contains("(function()"))
    }

    @Test
    fun `this operation's ids match the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(CollectionMapOperation().id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(CollectionFilterOperation().id))
    }
}
