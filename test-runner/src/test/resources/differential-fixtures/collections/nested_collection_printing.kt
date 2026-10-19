fun main() {
    println(listOf(1, 2, 3, 4).windowed(2))
    println(listOf(1, 2, 3, 4).chunked(2))
    println(listOf(listOf(1, 2), listOf(3)))
    println(listOf(1, 2, 3, 4, 5).zipWithNext())
    println(listOf(1, 2, 3).groupBy { it % 2 })
    println(mapOf("a" to listOf(1, 2), "b" to listOf(3)))
    println(listOf(setOf(1, 2), setOf(3)))
    println(setOf(listOf(1), listOf(2, 3)))
    println(listOf(1, 2, 3, 4).partition { it > 2 })
}
