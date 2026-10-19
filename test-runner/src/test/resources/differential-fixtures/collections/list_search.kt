fun main() {
    val numbers = listOf(10, 20, 30, 40, 50)
    println(numbers.first())
    println(numbers.last())
    println(numbers.find { it > 25 })
    println(numbers.find { it > 100 })
    println(numbers.indexOf(30))
    println(numbers.indexOf(999))
    println(numbers.indexOfFirst { it > 20 })
    println(numbers.indexOfLast { it > 20 })
    println(numbers.firstOrNull())
    println(numbers.lastOrNull())
    println(emptyList<Int>().firstOrNull())
    println(emptyList<Int>().lastOrNull())

    println(numbers.first { it > 25 })
    println(numbers.firstOrNull { it > 25 })
    println(numbers.firstOrNull { it > 1000 })
    println(numbers.last { it < 35 })
    println(numbers.lastOrNull { it < 35 })
    println(numbers.lastOrNull { it > 1000 })

    println(setOf(10, 20, 30).first { it > 15 })
}
