package kt2lua.transpiler.compatibility

import kotlin.test.Test
import kotlin.test.assertEquals

class DeclarationIdTest {
    @Test
    fun `round-trips a member function id with a simple parameter`() {
        val id = DeclarationId("kotlin.collections", "List", "get", listOf("Int"))
        assertEquals("kotlin.collections.List.get(Int)", id.toString())
        assertEquals(id, DeclarationId.parse(id.toString()))
    }

    @Test
    fun `round-trips a top-level function id with no receiver`() {
        val id = DeclarationId("kotlin.collections", null, "listOf", listOf("T"))
        assertEquals("kotlin.collections.listOf(T)", id.toString())
        assertEquals(id, DeclarationId.parse(id.toString()))
    }

    @Test
    fun `round-trips a property id with no parens`() {
        val id = DeclarationId("kotlin.collections", "List", "size", isProperty = true)
        assertEquals("kotlin.collections.List.size", id.toString())
        assertEquals(id, DeclarationId.parse(id.toString()))
    }

    @Test
    fun `round-trips a lambda-shaped parameter without splitting on its internal comma`() {
        val id = DeclarationId("kotlin.collections", "Iterable", "fold", listOf("R", "(R, T) -> R"))
        val text = id.toString()
        assertEquals("kotlin.collections.Iterable.fold(R, (R, T) -> R)", text)
        assertEquals(id, DeclarationId.parse(text))
    }

    @Test
    fun `round-trips a generic type-argument parameter without splitting on its internal comma`() {
        val id = DeclarationId("kotlin.collections", null, "mapOf", listOf("Pair<K, V>"))
        val text = id.toString()
        assertEquals(id, DeclarationId.parse(text))
        assertEquals(listOf("Pair<K, V>"), DeclarationId.parse(text).parameters)
    }

    @Test
    fun `round-trips a nested receiver type like Map Entry`() {
        val id = DeclarationId("kotlin.collections", "Map.Entry", "key", isProperty = true)
        val text = id.toString()
        assertEquals("kotlin.collections.Map.Entry.key", text)
        assertEquals(id, DeclarationId.parse(text))
    }

    @Test
    fun `two ids built the same way are equal and hash equal, for use as a map key`() {
        val a = DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R"))
        val b = DeclarationId.parse(a.toString())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
