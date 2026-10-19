fun main() {
    var mapCalls = 0
    val result = listOf(1, 2, 3).map {
        mapCalls += 1
        it * 10
    }
    println(result)
    println(mapCalls)

    val multiplier = 10
    val scaled = listOf(1, 2, 3).map { it * multiplier }
    println(scaled)
}
