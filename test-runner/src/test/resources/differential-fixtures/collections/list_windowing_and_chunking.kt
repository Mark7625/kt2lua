fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)
    println(numbers.windowed(2).size)
    println(numbers.windowed(2).map { it.sum() })
    println(numbers.windowed(3).size)
    println(numbers.chunked(2).size)
    println(numbers.chunked(2).map { it.sum() })
    println(numbers.zipWithNext().size)
    println(numbers.zipWithNext().map { it.first + it.second })

    val letters = listOf("a", "b", "c")
    println(numbers.zip(letters).size)
    println(numbers.zip(letters).map { it.first })
    println(numbers.zip(letters).map { it.second })

    println(listOf(3, 1, 2).sortedWith(compareByDescending { it }))
}
