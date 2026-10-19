package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexedAndNotNullOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `a module's own method of the same name is not shadowed for the gated tier`() {
        val code = emit("""
            import oop_lib
            fun main() { val a = oop_lib.mapNotNull(1) }
        """.trimIndent())
        assertTrue(code.contains("oop_lib.mapNotNull(1)"))
    }

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(MapIndexedOperation(), FilterIndexedOperation(), ForEachIndexedOperation(), MapNotNullOperation(), FilterNotNullOperation())) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
    }
}
