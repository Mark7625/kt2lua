package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListMutationOperationsTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `every mutation operation's id matches the bundled manifest exactly`() {
        val registry = CompatibilityRegistry.loadBundled()
        val supported = listOf(
            ListAddOperation(), ListAddAtOperation(), ListSetOperation(), ListClearOperation(), ListAddAllOperation(),
            ListSortOperation("sort", hasSelector = false, descending = false),
            ListSortOperation("sortBy", hasSelector = true, descending = false),
            ListSortOperation("sortByDescending", hasSelector = true, descending = true),
            ListReverseOperation(),
            ListFilterLambdaOperation("removeAll", keep = false), ListFilterLambdaOperation("retainAll", keep = true),
            ListFilterMembershipOperation("removeAll", keep = false), ListFilterMembershipOperation("retainAll", keep = true),
        )
        for (op in supported) {
            assertEquals(CompatibilityStatus.SUPPORTED, registry.status(op.id), "no 'supported' manifest entry for ${op.id}")
        }
        assertEquals(CompatibilityStatus.PARTIAL, registry.status(ListRemoveOperation().id))
    }
}
