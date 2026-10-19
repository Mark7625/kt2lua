package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupingOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `every operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (op in listOf(GroupByOperation(), AssociateOperation(), AssociateByOperation(), AssociateWithOperation())) {
            assertEquals(CompatibilityStatus.PARTIAL, registry.status(op.id), "no 'partial' manifest entry for ${op.id}")
        }
    }
}
