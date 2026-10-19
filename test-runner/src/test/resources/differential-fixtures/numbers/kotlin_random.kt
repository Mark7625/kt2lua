import kotlin.random.*

fun rollDie(rng: Random): Int = rng.nextInt(1, 7)

fun main() {
    val seeded = Random(42)
    println(seeded.nextInt())
    println(seeded.nextInt())
    println(seeded.nextInt(10))
    println(seeded.nextInt(5, 15))
    println(seeded.nextInt(0, 8))
    println(seeded.nextInt(-100, 100))
    println(seeded.nextDouble())
    println(seeded.nextDouble(10.0))
    println(seeded.nextDouble(5.0, 6.0))
    println(seeded.nextBoolean())
    println(seeded.nextBoolean())

    val zero = Random(0)
    println(zero.nextInt())
    println(zero.nextDouble())

    val negative = Random(-7)
    println(negative.nextInt(1000))
    println(negative.nextBoolean())

    val a = Random(2024)
    val b = Random(2024)
    println(a.nextInt() == b.nextInt())
    println(listOf(1, 2, 3, 4, 5).map { rollDie(a) } == listOf(1, 2, 3, 4, 5).map { rollDie(b) })

    val first = Random(7)
    val second = Random(8)
    second.nextInt()
    second.nextInt()
    println(first.nextInt() == Random(7).nextInt())

    val unsigned = Random(99)
    println(unsigned.nextUInt())
    println(unsigned.nextUInt())
    println(unsigned.nextUInt(10u))
    println(unsigned.nextUInt(5u, 15u))
    println(unsigned.nextUBytes(7).toList())
    println(unsigned.nextUBytes(4).size)
    println(Random(5).nextUBytes(10).toList())

    val ranged = Random(7)
    println(ranged.nextInt(1..6))
    println(ranged.nextInt(10 until 20))
    println(ranged.nextInt(0..0))
    println(ranged.asJavaRandom().nextInt(10) < 10)

    var longOk = true
    var longRangeOk = true
    var untilOk = true
    for (i in 1..200) {
        val v = Random.nextLong(100L)
        if (v < 0L || v >= 100L) longOk = false
        val w = Random.nextLong(5L, 10L)
        if (w < 5L || w >= 10L) longRangeOk = false
        val u = Random.nextUInt(3u, 6u)
        if (u < 3u || u >= 6u) untilOk = false
    }
    println(longOk)
    println(longRangeOk)
    println(untilOk)
    println(Random.nextLong(1L..3L) <= 3L)
    println(Random.nextULong(10uL) < 10uL)
    println(Random.nextUBytes(6).size)
    println(Random.nextInt(1..6) in 1..6)
    println(Random.nextULong() >= 0uL)
    println(Random.nextBoolean() || true)

    var allInRange = true
    var sawLow = false
    var sawHigh = false
    for (i in 1..300) {
        val roll = Random.nextInt(1, 7)
        if (roll < 1 || roll > 6) allInRange = false
        if (roll == 1) sawLow = true
        if (roll == 6) sawHigh = true
    }
    println(allInRange)
    println(sawLow && sawHigh)

    var unitOk = true
    for (i in 1..200) {
        val d = Random.nextDouble()
        if (d < 0.0 || d >= 1.0) unitOk = false
    }
    println(unitOk)
    println(Random.nextInt(5) < 5)
    println(Random.Default.nextInt(3, 4))
    val flip = Random.nextBoolean()
    println(flip || !flip)
    println(Random.nextDouble(2.0, 3.0) >= 2.0)
    val big = Random.nextInt()
    println(big >= Int.MIN_VALUE && big <= Int.MAX_VALUE)
}
