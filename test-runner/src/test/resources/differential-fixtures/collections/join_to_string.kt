fun main() {
    println(listOf(1, 2, 3).joinToString())
    println(listOf(1, 2, 3).joinToString(", "))
    println(listOf("a", "b").joinToString("-"))
    println(listOf(1, 2, 3).joinToString(separator = "|"))
    println(emptyList<Int>().joinToString(","))
    println(setOf(1, 2).joinToString("+"))
    println(listOf(1, 2, 3).map { it * 2 }.joinToString(" "))
}
