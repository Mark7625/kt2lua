import kotlin.time.AbstractLongTimeSource
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.TimedValue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class FakeClock : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    var now = 0L
    override fun read(): Long = now
}

fun age(mark: TimeMark): Duration = mark.elapsedNow()

fun older(a: ComparableTimeMark, b: ComparableTimeMark): Boolean = a < b

@OptIn(ExperimentalTime::class)
fun main() {
    val source = TestTimeSource()
    val start = source.markNow()
    println(start.elapsedNow())
    source += 5.seconds
    println(start.elapsedNow())
    source += 250.milliseconds
    println(start.elapsedNow())
    println(age(start))
    val later = source.markNow()
    source += 2.seconds
    println(later.elapsedNow())
    println(start.elapsedNow())
    println(later - start)
    println(start - later)
    val ahead = later + 10.seconds
    println(ahead.elapsedNow())
    println(ahead.hasPassedNow())
    println(ahead.hasNotPassedNow())
    println(later.hasPassedNow())
    println((later - 1.seconds).elapsedNow())
    println(later > start)
    println(start < later)
    println(later == later)
    println(start == later)

    println(older(start, later))
    println(older(later, start))

    val fake = FakeClock()
    val mark = fake.markNow()
    fake.now = 1500
    println(mark.elapsedNow())
    fake.now += 60000
    println(mark.elapsedNow())

    val real = TimeSource.Monotonic.markNow()
    println(real.elapsedNow() >= Duration.ZERO)
    println(real.elapsedNow().isNegative())

    var ran = 0
    val took = measureTime {
        ran += 1
    }
    println(ran)
    println(took >= Duration.ZERO)
    println(took.inWholeNanoseconds >= 0)

    val timed = measureTimedValue { 6 * 7 }
    println(timed.value)
    println(timed.duration >= Duration.ZERO)
    val (value, duration) = measureTimedValue { "result" }
    println(value)
    println(duration.isNegative())
    println(measureTimedValue { listOf(1, 2, 3).sum() }.value)
}
