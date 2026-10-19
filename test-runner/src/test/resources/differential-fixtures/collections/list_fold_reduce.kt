fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)
    println(numbers.fold(0) { acc, x -> acc + x })
    println(numbers.fold(1) { acc, x -> acc * x })
    println(numbers.reduce { acc, x -> acc + x })
}
