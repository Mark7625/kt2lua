fun main() {
    val numbers = listOf(5, 3, 1, 4, 1, 5, 9, 2, 6)
    println(numbers.sorted())
    println(numbers.sortedDescending())
    println(numbers.distinct())
    println(numbers.sortedBy { -it })
    println(listOf("bb", "a", "ccc").sortedBy { it.length })
}
