fun main() {
    val xs = mutableSetOf(1, 2, 3)
    xs.add(2)
    xs.add(4)
    println(xs.size)
    println(xs.contains(4))
    xs.remove(1)
    println(xs.contains(1))
    println(setOf(1, 2, 2, 3, 3, 3).size)
    println(setOf(1, 2, 3).map { it * 10 })
    println(setOf(1, 2, 3).sum())
    println(setOf(1, 2, 3).filter { it > 1 })
    println(setOf(1, 2, 3).distinct())
    println(setOf(1, 2, 3).fold(0) { acc, x -> acc + x })

    var trace = ""
    setOf(1, 2, 3).forEach { trace = "$trace$it " }
    println(trace)

    println(setOf(1, 2, 3, 4).groupBy { it % 2 }[0]?.size)
    println(setOf(1, 2, 3).mapIndexed { i, v -> i + v })
    println(setOf(1, 2, 3).max())
    println(setOf(1, 2, 3).min())
    println(setOf(3, 1, 2).sortedByDescending { it })
    println(setOf(1, 2, 3).take(2))

    val hs = hashSetOf(1, 2, 3)
    println(hs.size)
    println(emptySet<Int>().size)
    println(describe(setOf(5, 6)))
}

fun describe(xs: Set<Int>): Int = xs.size
