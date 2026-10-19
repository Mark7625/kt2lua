fun main() {
    val numbers = listOf(5, 3, 1, 4, 1, 5, 9, 2, 6)
    println(numbers.sortedByDescending { it })
    println(numbers.distinctBy { it % 3 })
    println(numbers.maxByOrNull { -it })
    println(numbers.maxOrNull())
}
