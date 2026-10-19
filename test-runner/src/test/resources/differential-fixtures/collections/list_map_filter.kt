fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)
    println(numbers.map { it * 2 })
    println(numbers.filter { it > 2 })
    println(numbers.map { it * 2 }.filter { it > 4 })
    println(numbers.sum())
    println(emptyList<Int>().map { it * 2 })
}
