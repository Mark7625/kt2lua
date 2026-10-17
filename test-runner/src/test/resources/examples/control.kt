fun max2(a: Int, b: Int): Int = if (a > b) a else b

fun classify(n: Int): String {
    if (n < 0) {
        return "negative"
    } else if (n == 0) {
        return "zero"
    } else {
        return "positive"
    }
}

fun main() {
    println(max2(10, 4))
    println(classify(-5))
    println(classify(0))
    println(classify(8))

    for (i in 0..10 step 3) {
        println(i)
    }
}
