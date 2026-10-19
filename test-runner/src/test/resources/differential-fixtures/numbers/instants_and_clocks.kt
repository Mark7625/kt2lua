import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.time.asClock
import kotlin.time.isDistantFuture
import kotlin.time.isDistantPast
import kotlin.time.toDuration
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalTime::class)
fun main() {
    val epoch = Instant.fromEpochSeconds(0)
    println(epoch)
    println(epoch.epochSeconds)
    println(epoch.nanosecondsOfSecond)
    println(Instant.fromEpochSeconds(1700000000))
    println(Instant.fromEpochSeconds(1700000000, 500000000))
    println(Instant.fromEpochSeconds(1700000000, 123456789))
    println(Instant.fromEpochMilliseconds(1700000000123))
    println(Instant.fromEpochMilliseconds(1700000000123).toEpochMilliseconds())

    val t = Instant.fromEpochSeconds(1700000000)
    println(t + 1.days)
    println(t + 90.minutes)
    println(t - 2.hours)
    println(t + 1500.milliseconds)
    println((t + 36.hours) - t)
    println(t - (t + 5.seconds))
    println(t < t + 1.seconds)
    println(t > t + 1.seconds)
    println(t == Instant.fromEpochSeconds(1700000000))
    println(t != epoch)
    println(listOf(t + 1.hours, t, t + 30.minutes).sorted())
    println(t.isDistantPast)
    println(t.isDistantFuture)
    println(Instant.DISTANT_PAST.isDistantPast)
    println(Instant.DISTANT_FUTURE.isDistantFuture)

    val now = Clock.System.now()
    println(now > epoch)
    println(now.epochSeconds > 1600000000)
    println(Clock.System.now() >= now)

    val source = TestTimeSource()
    val clock = source.asClock(t)
    println(clock.now())
    source += 10.minutes
    println(clock.now())

    val d = 90.seconds
    println(d.toJavaDuration().toKotlinDuration() == d)
    println(t.toJavaInstant().toKotlinInstant() == t)
}
