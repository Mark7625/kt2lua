fun main() {
    val numbers = listOf(10, 20, 30)
    println(numbers.mapIndexed { i, v -> i + v })
    println(numbers.filterIndexed { i, v -> i > 0 })

    var trace = ""
    numbers.forEachIndexed { i, v -> trace = "$trace$i:$v " }
    println(trace)

    val mixed: List<Int?> = listOf(1, null, 2, null, 3)
    println(mixed.mapNotNull { it })
    println(mixed.mapNotNull { if (it != null) it * 10 else null })
}
