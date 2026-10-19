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
    fun `partition builds a first-second pair of Lists`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3, 4); val p = xs.partition { it % 2 == 0 } }")
        assertTrue(code.contains("local p = {first = __match"))
    }

    @Test
    fun `flatten concatenates inner collections`() {
        val code = emit("fun main() { val xs = listOf(listOf(1, 2), listOf(3)); val ys = xs.flatten() }")
        assertTrue(code.contains("for _, __inner in ipairs(xs) do"))
    }

    @Test
    fun `reversed does not mutate the receiver`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); val ys = xs.reversed() }")
        assertTrue(code.contains("local ys = {}"))
        assertTrue(code.contains("ys[__i] = xs[__n"))
    }

    @Test
    fun `zip and zipWithNext build first-second pairs`() {
        val code = emit("fun main() { val xs = listOf(1, 2); val ys = listOf(\"a\", \"b\"); val a = xs.zip(ys); val b = xs.zipWithNext() }")
        assertTrue(code.contains("math.min(#xs, #ys)"))
        assertTrue(code.contains("#xs - 1"))
    }

    @Test
    fun `windowed groups into fixed-size overlapping sub-lists`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3, 4); val ys = xs.windowed(2) }")
        assertTrue(code.contains("#xs - (2) + 1"))
    }

    @Test
    fun `sortedWith uses the comparator as a plain 2-arg function`() {
        val code = emit("fun main() { val xs = listOf(3, 1, 2); val ys = xs.sortedWith { a, b -> a - b } }")
        assertTrue(code.contains("table.sort("))
    }

    @Test
    fun `toList, toSet and toMap build fresh copies in the target representation`() {
        val a = emit("fun main() { val xs = listOf(1, 2, 2); val ys = xs.toSet() }")
        assertTrue(a.contains("local ys = {list = {}, has = {}}"))
        val b = emit("""fun main() { val xs = listOf("a" to 1); val m = xs.toMap() }""")
        assertTrue(b.contains("local m = {keys = {}, map = {}}"))
        val c = emit("fun main() { val xs = listOf(1, 2); val ys = xs.toMutableList() }")
        assertTrue(c.contains("local ys = {}"))
    }

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
