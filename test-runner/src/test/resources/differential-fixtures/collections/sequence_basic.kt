fun main() {
    val result = listOf(1, 2, 3, 4, 5)
        .asSequence()
        .map { it * 2 }
        .filter { it > 4 }
        .toList()
    println(result)

    println(listOf(1, 2, 3, 4, 5).asSequence().take(2).toList())
    println(listOf(1, 2, 3, 4, 5).asSequence().sum())
}
