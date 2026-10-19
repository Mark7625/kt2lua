fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)
    println(numbers.take(2))
    println(numbers.drop(2))
    println(numbers.takeLast(2))
    println(numbers.dropLast(2))
    println(numbers.takeWhile { it < 3 })
    println(numbers.dropWhile { it < 3 })
}
