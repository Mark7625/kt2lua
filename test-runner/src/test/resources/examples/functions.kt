fun factorial(n: Int): Int {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}

fun isEven(n: Int): Boolean = n % 2 == 0

fun main() {
    println(factorial(5))
    println(isEven(4))
    println(isEven(7))

    var sum = 0
    for (i in 1..5) {
        sum += i
    }
    println(sum)

    var count = 0
    while (count < 3) {
        println("count is $count")
        count += 1
    }
}
