fun main() {
    val numbers = listOf(1, 2, 3, 4, 5, 6)
    val grouped = numbers.groupBy { if (it % 2 == 0) "even" else "odd" }
    println(grouped["even"]?.size)
    println(grouped["odd"]?.size)
    println(grouped["even"]?.sum())

    val byDouble = numbers.associateWith { it * 2 }
    println(byDouble[3])

    val byId = listOf("a", "bb", "ccc").associateBy { it.length }
    println(byId[2])
}
