fun main() {
    println((1..5).map { it * 2 })
    println((1..3).toList())
    println((1 until 4).toList())
    println((5 downTo 1).toList())
    println((1..5).filter { it % 2 == 1 })
    println((1..4).sum())
    println((1..10).count())

    val n = 3
    println((1..n).toList())
    println((0 until n).map { it + 1 })

    var inside = 0
    for (x in listOf(0, 1, 5, 10, 11)) {
        if (x in 1..10) inside += 1
    }
    println(inside)
    println(5 in 1 until 5)
    println(4 in 1 until 5)
    println(7 !in 1..3)
    println(1 + 1 in 1..3)

    var total = 0
    for (v in (1..4)) {
        total += v
    }
    println(total)
}
