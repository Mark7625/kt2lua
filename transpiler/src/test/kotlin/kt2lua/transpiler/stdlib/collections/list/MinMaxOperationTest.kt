package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinMaxOperationTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a module's own method of the same name is not shadowed`() {
        val code = emit("""
            import oop_lib
            fun main() { val m = oop_lib.max() }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.max()"))
    }

    @Test
    fun `every spelling's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val ops = listOf(
            MinMaxOperation("minOrNull", hasSelector = false, findMax = false),
            MinMaxOperation("min", hasSelector = false, findMax = false),
            MinMaxOperation("maxOrNull", hasSelector = false, findMax = true),
            MinMaxOperation("max", hasSelector = false, findMax = true),
            MinMaxOperation("minByOrNull", hasSelector = true, findMax = false),
            MinMaxOperation("maxByOrNull", hasSelector = true, findMax = true),
        )
        for (op in ops) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
