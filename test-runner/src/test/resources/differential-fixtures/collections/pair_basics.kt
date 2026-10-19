fun minMax(xs: List<Int>): Pair<Int, Int> = Pair(xs.min(), xs.max())

fun main() {
    val p = 1 to "one"
    println(p.first)
    println(p.second)
    println(p)
    val q = Pair(3, 4)
    println(q.first + q.second)
    println(q)
    val (lo, hi) = minMax(listOf(5, 2, 9))
    println(lo)
    println(hi)
    val pairs = listOf(1 to "a", 2 to "b")
    println(pairs.map { it.first })
    println(pairs.map { it.second })
    println(pairs.toMap().size)
}
