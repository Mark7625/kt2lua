fun sideEffect(label: String, value: Boolean): Boolean {
    println("eval:$label")
    return value
}

fun main() {
    println(1 < 2)
    println(2 <= 2)
    println(3 > 2)
    println(2 >= 3)
    println(1 == 1)
    println(1 != 2)
    println(true && false)
    println(true || false)

    // short-circuit: the right side must not evaluate
    if (sideEffect("left-false", false) && sideEffect("right", true)) {
        println("unreachable")
    }
    if (sideEffect("left-true", true) || sideEffect("right2", true)) {
        println("short-circuited-or")
    }
}
