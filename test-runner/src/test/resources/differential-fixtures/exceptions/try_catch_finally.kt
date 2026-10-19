fun safeDivide(a: Int, b: Int): Int {
    var result = -1
    try {
        result = a / b
    } catch (e: Exception) {
        result = -1
    } finally {
        println("divide-finally")
    }
    return result
}

fun main() {
    println(safeDivide(10, 2))

    var caught = false
    try {
        throw RuntimeException("boom")
    } catch (e: Exception) {
        caught = true
    }
    println(caught)
}
