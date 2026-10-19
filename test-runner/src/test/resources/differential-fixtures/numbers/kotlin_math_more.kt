import kotlin.math.IEEErem
import kotlin.math.absoluteValue
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.cosh
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.nextDown
import kotlin.math.nextTowards
import kotlin.math.nextUp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sinh
import kotlin.math.tanh
import kotlin.math.ulp
import kotlin.math.withSign

fun twice(x: Double): Double = x * 2

fun main() {
    println(asin(0.5))
    println(acos(0.5))
    println(atan(1.0))
    println(atan2(1.0, 2.0))
    println(atan2(-1.0, -1.0))
    println(sinh(1.0))
    println(cosh(1.0))
    println(tanh(0.5))
    println(ln(10.0))
    println(ln(1.0))

    println(asinh(1.0))
    println(asinh(-2.5))
    println(asinh(twice(3.0)))
    println(acosh(2.0))
    println(acosh(twice(5.0)))
    println(atanh(0.5))
    println(atanh(-0.25))

    println(cbrt(27.0))
    println(cbrt(64.0))
    println(cbrt(-8.0))
    println(cbrt(2.0))
    println(expm1(1.0))
    println(expm1(1.0E-10))
    println(ln1p(1.0))
    println(ln1p(1.0E-10))

    println(5.0.IEEErem(3.0))
    println(7.0.IEEErem(2.0))
    println(10.0.IEEErem(4.0))

    println((-4.5).absoluteValue)
    println((-7).absoluteValue)
    println(3.25.absoluteValue)

    println(1.0.ulp)
    println(1024.0.ulp)
    println(0.1.ulp)
    println(1.0.nextUp())
    println(1.0.nextDown())
    println((-1.0).nextUp())
    println(0.5.nextDown())
    println(2.0.nextTowards(3.0))
    println(2.0.nextTowards(1.0))
    println(0.0.nextUp())

    println(3.5.withSign(-1.0))
    println((-3.5).withSign(2.0))
    println(twice(2.0).withSign(-0.0))

    println(2.5.roundToInt())
    println(2.4.roundToInt())
    println((-2.6).roundToInt())
    println(1234.5.roundToLong())
}
