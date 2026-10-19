fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)
    println(numbers.any { it > 4 })
    println(numbers.any { it > 10 })
    println(numbers.all { it > 0 })
    println(numbers.all { it > 2 })
    println(numbers.none { it > 10 })
    println(numbers.count())
    println(numbers.count { it % 2 == 0 })
    println(numbers.sumOf { it * it })
    println(numbers.min())
    println(numbers.max())
    println(numbers.minByOrNull { -it })
}
