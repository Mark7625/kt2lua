import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.times
import kotlin.time.toDuration

fun describe(d: Duration): String = "took $d"

fun main() {
    println(5.seconds)
    println(1.5.seconds)
    println(90.seconds)
    println(100.milliseconds)
    println(250.microseconds)
    println(42.nanoseconds)
    println(2.hours + 30.minutes)
    println(3.days)
    println(1.days + 2.hours + 3.minutes + 4.seconds + 5.milliseconds)
    println(Duration.ZERO)
    println(Duration.INFINITE)
    println(-Duration.INFINITE)
    println((-90).seconds)
    println((-5).seconds)
    println(1234.milliseconds)
    println(1234567.microseconds)
    println(0.001.seconds)
    println(describe(7.minutes))

    val a = 90.seconds
    val b = 30.seconds
    println(a + b)
    println(a - b)
    println(b - a)
    println(a * 2)
    println(a * 1.5)
    println(2 * b)
    println(a / 3)
    println(a / b)
    println(a / 4.0)
    println(-a)
    println(a > b)
    println(a < b)
    println(a == 90.seconds)
    println(a == b)
    println(a != b)
    println(a >= 1.minutes + 30.seconds)
    println(a.compareTo(b))
    println(b.compareTo(a))
    println(listOf(3.seconds, 1.seconds, 2.seconds).sorted())
    println(listOf(3.seconds, 1.seconds, 2.seconds).max())
    println(listOf(30.seconds, 1.minutes).map { it * 2 })

    println(a.inWholeSeconds)
    println(a.inWholeMilliseconds)
    println(a.inWholeMinutes)
    println(a.inWholeHours)
    println(a.inWholeNanoseconds)
    println(a.inWholeMicroseconds)
    println(a.inWholeDays)
    println(1234.milliseconds.inWholeSeconds)
    println((-1234).milliseconds.inWholeSeconds)
    println(a.inWholeSeconds / 4)

    println(a.toDouble(DurationUnit.MINUTES))
    println(a.toDouble(DurationUnit.SECONDS))
    println(a.toLong(DurationUnit.MINUTES))
    println(a.toInt(DurationUnit.MILLISECONDS))
    println(a.toString(DurationUnit.MINUTES, 2))
    println(a.toString(DurationUnit.SECONDS))
    println(1500.milliseconds.toString(DurationUnit.SECONDS, 3))
    println(a.toIsoString())
    println(1.hours.toIsoString())
    println((2.hours + 5.seconds + 500.milliseconds).toIsoString())
    println(0.seconds.toIsoString())
    println((-90).seconds.toIsoString())

    println(a.isPositive())
    println((-a).isNegative())
    println(Duration.ZERO.isPositive())
    println(Duration.INFINITE.isInfinite())
    println(a.isFinite())
    println((-a).absoluteValue)

    a.toComponents { minutes, seconds, nanoseconds -> println("$minutes|$seconds|$nanoseconds") }
    (1.days + 3.hours + 4.minutes + 5.seconds + 6.milliseconds).toComponents { days, hours, minutes, seconds, nanoseconds ->
        println("$days|$hours|$minutes|$seconds|$nanoseconds")
    }
    (26.hours + 4.minutes + 5.seconds).toComponents { hours, minutes, seconds, nanoseconds -> println("$hours|$minutes|$seconds|$nanoseconds") }
    a.toComponents { seconds, nanoseconds -> println("$seconds|$nanoseconds") }

    println(5.toDuration(DurationUnit.MINUTES))
    println(2.5.toDuration(DurationUnit.HOURS))
    println(7L.toDuration(DurationUnit.DAYS))
    println(a.coerceAtLeast(2.minutes))
    println(a.coerceAtMost(1.minutes))
    println(a.coerceIn(1.minutes, 80.seconds))
    println(500.milliseconds.coerceIn(1.seconds, 2.seconds))

    var total = Duration.ZERO
    for (i in 1..4) total += i.seconds
    println(total)
    var remaining = 10.seconds
    remaining -= 3.seconds
    println(remaining)
    println(if (a > 1.minutes) "long" else "short")
    println(DurationUnit.SECONDS)
}
