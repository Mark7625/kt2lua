package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapMutationOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `every mutation operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(MapPutOperation().id))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(MapRemoveOperation().id))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(MapClearOperation().id))
    }
}
