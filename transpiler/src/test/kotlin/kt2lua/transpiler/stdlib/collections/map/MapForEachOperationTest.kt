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
    fun `this operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(MapForEachOperation().id))
    }
}
