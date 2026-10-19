import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun main() {
    var n = 10
    n += 5
    println(n)
    n -= 3
    println(n)
    n *= 2
    println(n)
    n /= 5
    println(n)
    n %= 3
    println(n)

    val d = 2.5
    println(d * 2)
    println(d + 1)
    println(d - 0.5)
    println(d / 2)
    println(10 / 4.0)

    println(3 + 0.5)
    println(5.toDouble())
    println(7.9.toInt())
    println(-7.9.toInt())

    println(abs(-4))
    println(abs(-4.5))
    println(max(3, 9))
    println(min(3, 9))
    println(sqrt(16.0))

    println(1.5 < 2.5)
    println(2.0 == 2.0)
    println(0.1 + 0.2 > 0.3)

    println(2 * 3 + 4 * 5 - 6 / 2)
    println(-(3 + 4))
    println(Int.MAX_VALUE)
    println(Int.MIN_VALUE)
    println(1_000_000)
}
