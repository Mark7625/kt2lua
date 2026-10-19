package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistinctOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val ys = oop_lib.distinct() }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.distinct()"))
    }

    @Test
    fun `every spelling's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(DistinctOperation("distinct", hasSelector = false).id))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(DistinctOperation("distinctBy", hasSelector = true).id))
    }
}
