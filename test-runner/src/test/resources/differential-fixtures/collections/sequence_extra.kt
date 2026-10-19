fun main() {
    println(sequenceOf(1, 2, 3).map { it + 1 }.toList())
    println(generateSequence(1) { it * 2 }.take(5).toList())
    println(listOf(5, 3, 8).asSequence().filter { it > 3 }.map { it * 10 }.toList())
    println(listOf(1, 2, 3, 4).asSequence().map { it * it }.sum())
    println(listOf(1, 2, 3, 4, 5, 6).asSequence().filter { it % 2 == 0 }.take(2).toList())
}
