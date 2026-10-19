var calls = 0

fun getNumbers(): List<Int> {
    calls += 1
    return listOf(1, 2, 3)
}

fun main() {
    println(getNumbers().map { it * 2 })
    println(calls)

    var mapCalls = 0
    val result = listOf(1, 2, 3).map {
        mapCalls += 1
        it * 10
    }
    println(result)
    println(mapCalls)
}
