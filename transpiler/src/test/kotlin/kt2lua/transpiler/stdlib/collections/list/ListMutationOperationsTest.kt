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
    fun `add(element) appends, add(index, element) inserts`() {
        val code = emit("fun main() { val xs = mutableListOf(1, 2); xs.add(3); xs.add(0, 9) }")
        assertTrue(code.contains("table.insert(xs, 3)"))
        assertTrue(code.contains("table.insert(xs, (0) + 1, 9)"))
    }

    @Test
    fun `remove searches structurally and set assigns by index`() {
        val code = emit("fun main() { val xs = mutableListOf(1, 2, 3); xs.remove(2); xs.set(0, 99) }")
        assertTrue(code.contains("for __i, __v in ipairs(xs) do if (__v == 2) then table.remove(xs, __i); break end end"))
        assertTrue(code.contains("xs[(0) + 1] = 99"))
    }

    @Test
    fun `clear empties in place and addAll appends every element`() {
        val code = emit("fun main() { val xs = mutableListOf(1); val ys = mutableListOf(2, 3); xs.clear(); xs.addAll(ys) }")
        assertTrue(code.contains("for __i = #xs, 1, -1 do table.remove(xs, __i) end"))
        assertTrue(code.contains("for _, __e in ipairs(ys) do table.insert(xs, __e) end"))
    }

    @Test
    fun `sort, sortBy and sortByDescending rewrite the same table in place`() {
        val code = emit("""
            fun main() {
                val xs = mutableListOf(3, 1, 2)
                xs.sort()
                xs.sortBy { -it }
                xs.sortByDescending { it }
            }
        """.trimIndent())
        assertTrue(Regex("""for __i, __v in ipairs\(__sorted\d+\) do xs\[__i] = __v end""").containsMatchIn(code), "expected an in-place sort copy-back, got:\n$code")
        assertTrue(code.contains("table.sort("))
    }

    @Test
    fun `reverse swaps pairs in place`() {
        val code = emit("fun main() { val xs = mutableListOf(1, 2, 3); xs.reverse() }")
        assertTrue(Regex("""math\.floor\(__n\d+ / 2\)""").containsMatchIn(code))
    }

    @Test
    fun `removeAll and retainAll dispatch to the lambda or membership overload by argument shape`() {
        val code = emit("""
            fun main() {
                val xs = mutableListOf(1, 2, 3)
                val ys = mutableListOf(4, 5)
                xs.removeAll { it > 1 }
                xs.retainAll { it > 1 }
                xs.removeAll(ys)
                xs.retainAll(ys)
            }
        """.trimIndent())
        assertTrue(code.contains("if not ((it > 1)) then table.insert(__keep"), "expected a lambda-predicate removeAll, got:\n$code")
        assertTrue(code.contains("if (it > 1) then table.insert(__keep"), "expected a lambda-predicate retainAll, got:\n$code")
        assertTrue(Regex("""for _, __v in ipairs\(ys\) do __set\d+\[__v] = true end""").containsMatchIn(code), "expected a membership removeAll/retainAll, got:\n$code")
    }

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
