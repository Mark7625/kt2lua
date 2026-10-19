fun main() {
    val numbers = listOf(1, 2, 3, 4, 5, 6)
    val (evens, odds) = numbers.partition { it % 2 == 0 }
    println(evens.size)
    println(odds.size)
    println(evens.sum())

    println(listOf(listOf(1, 2), listOf(3, 4), listOf<Int>()).flatten())
    println(listOf(1, 2, 3).reversed())
}
