fun main() {
    val numbers = listOf(1, 2, 2, 3)
    println(numbers.toList())
    println(numbers.toMutableList())

    val asSet = numbers.toSet()
    println(asSet.size)
    val asMutableSet = numbers.toMutableSet()
    println(asMutableSet.size)

    val pairs = listOf(1 to "one", 2 to "two")
    val asMap = pairs.toMap()
    println(asMap[1])
    println(asMap[2])
}
