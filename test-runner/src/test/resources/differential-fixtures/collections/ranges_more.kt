fun sumRange(r: IntRange): Int {
    var total = 0
    for (x in r) total += x
    return total
}

fun describe(r: IntProgression): String = "${r.first}..${r.last}"

fun main() {
    println((1..10 step 3).toList())
    println((10 downTo 1 step 4).toList())
    println((1 until 10 step 4).toList())
    println((1..10).step(5).toList())
    println(1.rangeTo(4).toList())
    println(1.until(4).toList())
    println(4.downTo(1).toList())
    println((0..<4).toList())
    println((1..5).reversed().toList())
    println((1..5).first())
    println((1..5).last())
    println((1..5).firstOrNull())
    println((3..2).firstOrNull())
    println((3..2).lastOrNull())
    println((1..5).lastOrNull())

    val r: IntRange = 2..6
    println(r.first)
    println(r.last)
    println(r.toList())
    println(3 in r)
    println(9 in r)
    println(r.contains(4))
    println(sumRange(r))
    println(sumRange(1..4))
    println(describe(1..9 step 2))

    val long: LongRange = 1L..4L
    println(long.toList())
    val unsigned: UIntRange = 1u..3u
    println(unsigned.toList())
    val open: OpenEndRange<Int> = 1..<4
    println(open.contains(3))
    println(open.contains(4))
    val closed: ClosedRange<Int> = 1..3
    println(closed.contains(2))

    val f: ClosedFloatingPointRange<Double> = 0.0..1.0
    println(0.5 in f)
    println(2.0 in f)
    println(f.contains(1.0))
    println(f.start)
    println(f.endInclusive)
    println(0.25 in 0.0..0.5)

    println(5.coerceAtLeast(7))
    println(5.coerceAtLeast(2))
    println(5.coerceAtMost(3))
    println(5.coerceAtMost(9))
    println(5.coerceIn(1, 3))
    println((-2).coerceIn(0, 10))
    println(7.coerceIn(0, 10))
    println(5.coerceIn(1..3))
    println(2.5.coerceIn(0.0, 1.0))
    println(0.75.coerceAtLeast(0.5))

    println((1..6).random() in 1..6)
    println((1..6).randomOrNull()!! in 1..6)
    println((5..1).randomOrNull())
    println(listOf(4, 5, 6).random() in 4..6)
    println(setOf(7, 8).random() in 7..8)
    println(emptyList<Int>().randomOrNull())
}
