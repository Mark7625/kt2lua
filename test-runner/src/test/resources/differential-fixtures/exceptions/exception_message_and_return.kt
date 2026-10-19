fun risky(n: Int): Int {
    if (n < 0) throw IllegalArgumentException("negative: $n")
    return n * 2
}

fun withFinally(n: Int): Int {
    try {
        return risky(n)
    } catch (e: Exception) {
        return -1
    } finally {
        println("cleanup $n")
    }
}

fun firstEven(xs: List<Int>): Int {
    for (x in xs) {
        try {
            if (x % 2 == 0) return x
        } finally {
            println("checked $x")
        }
    }
    return -1
}

fun nested(n: Int): String {
    try {
        try {
            if (n > 0) return "inner-return"
            throw RuntimeException("inner-fail")
        } finally {
            println("inner-finally")
        }
    } catch (e: Exception) {
        return "outer-caught ${e.message}"
    }
}

fun catchThrows(): String {
    try {
        try {
            throw RuntimeException("first")
        } catch (e: Exception) {
            throw RuntimeException("second from ${e.message}")
        } finally {
            println("finally-after-rethrow")
        }
    } catch (e: Exception) {
        return e.message ?: "none"
    }
}

fun main() {
    try {
        throw RuntimeException("boom")
    } catch (e: Exception) {
        println(e.message)
    }

    try {
        risky(-2)
    } catch (e: Exception) {
        println("caught ${e.message}")
    }

    println(withFinally(3))
    println(withFinally(-3))
    println(firstEven(listOf(1, 3, 4, 6)))
    println(firstEven(listOf(1, 3)))
    println(nested(1))
    println(nested(0))
    println(catchThrows())

    var caught = ""
    try {
        throw RuntimeException("x")
    } catch (e: Exception) {
        caught = "got ${e.message}"
    } finally {
        println("always")
    }
    println(caught)
}
