import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.truncate

fun circleArea(r: Double): Double = PI * r * r

fun main() {
    println(sqrt(16.0))
    println(sqrt(2.0))
    println(abs(-4))
    println(abs(-4.5))
    println(max(3, 9))
    println(min(3, 9))
    println(max(2.5, -1.0))
    println(min(2.5, -1.0))

    println(2.0.pow(10))
    println(9.0.pow(0.5))
    println(2.0.pow(-2))

    println(floor(3.7))
    println(floor(-3.2))
    println(ceil(3.2))
    println(ceil(-3.7))
    println(round(2.5))
    println(round(3.5))
    println(round(-2.5))
    println(round(2.4))
    println(round(2.6))
    println(sign(-8.0))
    println(sign(0.0))
    println(sign(12.5))
    println(truncate(3.9))
    println(truncate(-3.9))

    println(PI)
    println(E)
    println(circleArea(2.0))

    println(sin(0.0))
    println(cos(0.0))
    println(tan(0.0))
    println(sin(PI / 2))
    println(cos(PI))

    println(log(8.0, 2.0))
    println(log(100.0, 10.0))
    println(log2(1024.0))
    println(log10(1000.0))
    println(log10(0.001))
    println(exp(0.0))
    println(exp(1.0))
    println(hypot(3.0, 4.0))
    println(hypot(5.0, 12.0))

    println(2.5.roundToInt())
    println(3.4.roundToInt())
    println(-2.5.roundToInt())

    println(1.0 / 3.0)
    println(1.0E-4)
    println(123456789.0)
    println(0.001)
    println(1.0E7)
}
