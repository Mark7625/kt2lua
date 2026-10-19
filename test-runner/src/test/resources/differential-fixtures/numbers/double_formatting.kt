class Temperature(val celsius: Double) {
    fun fahrenheit(): Double = celsius * 9.0 / 5.0 + 32.0
}

fun half(n: Int): Double = n / 2.0

fun main() {
    val celsius = 100.0
    println(celsius * 9.0 / 5.0 + 32.0)
    println(Temperature(100.0).fahrenheit())
    println(Temperature(37.5).celsius)
    println(half(10))
    println(half(7))
    println("value=${half(4)}")
    println(3.0.toString())
    println(10.toDouble() / 4)
    val ratio: Double = 6.0 / 3.0
    println(ratio)
}
