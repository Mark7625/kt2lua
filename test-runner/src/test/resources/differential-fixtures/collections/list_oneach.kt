var makeListCalls = 0

fun makeList(): List<Int> {
    makeListCalls += 1
    return listOf(1, 2, 3)
}

fun main() {
    var trace = ""
    val xs = listOf(1, 2, 3)
    val ys = xs.onEach { trace = "$trace$it " }
    println(trace)
    println(ys)

    // onEach must evaluate a side-effecting receiver exactly once
    makeList().onEach { }
    println(makeListCalls)

    // works for a Set receiver too
    var setTrace = ""
    setOf(1, 2, 3).onEach { setTrace = "$setTrace$it " }
    println(setTrace)
}
