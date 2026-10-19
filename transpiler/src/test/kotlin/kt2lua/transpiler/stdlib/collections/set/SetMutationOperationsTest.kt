package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetMutationOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `add and remove dispatch through the dedup-tracked representation`() {
        val code = emit("fun main() { val xs = mutableSetOf(1, 2); xs.add(3); xs.remove(1) }")
        assertTrue(code.contains("if not xs.has[3] then table.insert(xs.list, 3); xs.has[3] = true end"))
        assertTrue(code.contains("if xs.has[1] then"))
    }

    @Test
    fun `clear empties both the list and the has table`() {
        val code = emit("fun main() { val xs = mutableSetOf(1, 2); xs.clear() }")
        assertTrue(code.contains("xs.list = {}"))
        assertTrue(code.contains("xs.has = {}"))
    }

    @Test
    fun `every mutation operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(SetAddOperation().id))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(SetRemoveOperation().id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(SetClearOperation().id))
    }
}
