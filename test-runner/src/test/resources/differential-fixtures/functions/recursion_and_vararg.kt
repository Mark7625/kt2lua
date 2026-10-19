fun factorial(n: Int): Int {
    if (n <= 1) return 1
    return n * factorial(n - 1)
}

fun sumAll(vararg numbers: Int): Int {
    var total = 0
    for (n in numbers) {
        total += n
    }
    return total
}

fun main() {
    println(factorial(5))
    println(factorial(1))
    println(sumAll(1, 2, 3, 4))
    println(sumAll())
}
