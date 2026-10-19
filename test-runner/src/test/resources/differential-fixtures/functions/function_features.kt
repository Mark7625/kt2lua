val greeting = "hi"
var globalCounter = 0

fun double(n: Int): Int = n * 2

fun addTwo(a: Int, b: Int): Int {
    return a + b
}

fun logValue(value: Int) {
    globalCounter += value
}

fun firstNegative(xs: List<Int>): Int {
    for (x in xs) {
        if (x < 0) return x
    }
    return 0
}

fun describe(name: String, age: Int): String = "$name is $age"

fun main() {
    println(greeting)
    println(double(4))
    println(addTwo(2, 3))
    logValue(3)
    logValue(4)
    println(globalCounter)

    println(listOf(1, 2, 3).map(::double))

    println(firstNegative(listOf(3, 2, -1, -5)))
    println(firstNegative(listOf(1, 2)))

    println(describe("Sam", 30))
    println(describe(age = 30, name = "Sam"))
    println(describe("Ann", age = 41))

    val shadow = 1
    run {
        val inner = shadow + 1
        println(inner)
    }
    println(shadow)
}
