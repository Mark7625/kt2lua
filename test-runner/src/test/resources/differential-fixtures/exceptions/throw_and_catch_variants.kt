fun risky(n: Int): Int {
    if (n < 0) throw IllegalArgumentException("negative: $n")
    return n * 2
}

fun withFinally(n: Int): Int {
    var result = 0
    try {
        result = risky(n)
    } catch (e: Exception) {
        result = -1
    } finally {
        println("cleanup $n")
    }
    return result
}

fun main() {
    try {
        println(risky(4))
        println(risky(-2))
        println("not reached")
    } catch (e: Exception) {
        println("caught")
    }

    println(withFinally(3))
    println(withFinally(-3))

    try {
        try {
            throw RuntimeException("inner")
        } finally {
            println("inner-finally")
        }
    } catch (e: Exception) {
        println("outer caught")
    }

    try {
        try {
            throw IllegalStateException("first")
        } catch (e: Exception) {
            throw RuntimeException("second")
        }
    } catch (e: Exception) {
        println("second caught")
    }

    var attempts = 0
    for (i in 1..3) {
        try {
            attempts += 1
            if (i % 2 == 1) throw RuntimeException("odd")
        } catch (e: Exception) {
            println("odd $i")
        }
    }
    println(attempts)
}
