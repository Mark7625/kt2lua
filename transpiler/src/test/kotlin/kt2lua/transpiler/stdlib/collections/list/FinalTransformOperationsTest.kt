package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinalTransformOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val ops = listOf(
            FlattenOperation(), ReversedOperation(), ZipOperation(), ZipWithNextOperation(),
            SortedWithOperation(),
            ToListOperation("toList"), ToListOperation("toMutableList"),
            ToSetOperation("toSet"), ToSetOperation("toMutableSet"),
            ToMapOperation(),
        )
        for (op in ops) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(WindowedOperation().id))
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(PartitionOperation().id))
    }
}
