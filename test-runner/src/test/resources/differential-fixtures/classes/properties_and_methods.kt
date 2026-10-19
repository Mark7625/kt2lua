class Counter(val label: String, var count: Int) {
    var step: Int = 1
    val doubled: Int = count * 2

    fun increment() {
        count += step
    }

    fun incrementTwice() {
        increment()
        increment()
    }

    fun describe(): String = "$label=$count"

    fun reset(): Counter {
        count = 0
        return this
    }
}

class Temperature(val celsius: Double) {
    fun fahrenheit(): Double = celsius * 9.0 / 5.0 + 32.0
    override fun toString(): String = "Temperature(${celsius}C)"
}

fun main() {
    val c = Counter("hits", 0)
    c.increment()
    c.incrementTwice()
    println(c.count)
    println(c.describe())

    c.step = 5
    c.increment()
    println(c.count)
    println(c.doubled)

    println(c.reset().count)

    val counters = listOf(Counter("a", 1), Counter("b", 2), Counter("c", 3))
    println(counters.map { it.count })
    println(counters.filter { it.count > 1 }.map { it.label })
    println(counters.sumOf { it.count })
    counters.forEach { it.increment() }
    println(counters.map { it.describe() })

    val t = Temperature(37.5)
    println(t.fahrenheit())
    println(t)
    println("value: $t")
}
