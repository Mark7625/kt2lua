import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toDurationUnit
import kotlin.time.toTimeUnit

fun main() {
    println(TimeUnit.SECONDS.toDurationUnit())
    println(DurationUnit.MINUTES.toTimeUnit())
    println(90.seconds.toDouble(TimeUnit.MINUTES.toDurationUnit()))
    println(3.toDuration(TimeUnit.HOURS.toDurationUnit()))
    println(DurationUnit.DAYS)
}
