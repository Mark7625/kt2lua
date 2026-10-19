import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

fun work(): Int {
    var total = 0
    for (i in 1..2000) total += i
    return total
}

fun main() {
    var ran = 0
    val millis = measureTimeMillis {
        ran = work()
    }
    println(ran)
    println(millis >= 0)

    val nanos = measureNanoTime {
        ran += work()
    }
    println(ran)
    println(nanos >= 0)

    var calls = 0
    val elapsed = measureTimeMillis { calls += 1 }
    println(calls)
    println(elapsed < 60000)
    println(measureTimeMillis { } >= 0)
}
