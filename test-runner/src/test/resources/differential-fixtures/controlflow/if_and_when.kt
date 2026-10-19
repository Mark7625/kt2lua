fun classify(n: Int): String {
    return if (n < 0) "negative" else if (n == 0) "zero" else "positive"
}

fun describe(n: Int): String = when (n) {
    0 -> "zero"
    1, 2 -> "small"
    3, 4, 5, 6, 7, 8, 9, 10 -> "medium"
    else -> "large"
}

fun main() {
    println(classify(-5))
    println(classify(0))
    println(classify(5))
    for (n in listOf(0, 1, 2, 5, 100)) {
        println(describe(n))
    }
}
