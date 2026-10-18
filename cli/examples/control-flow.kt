// if/else, `when`, and every loop form kt2lua supports.
//
// Transpile + run:
//   cli/build/install/cli/bin/cli examples/control-flow.kt --run

fun describe(n: Int): String {
    // `when` with a subject, comma-separated conditions, and an `in range` check — compiles to
    // if/elseif/else, with `in a..b` becoming a bounds check against the subject.
    return when (n) {
        0 -> "zero"
        1, 2, 3 -> "small"
        in 4..9 -> "single digit"
        else -> "big"
    }
}

fun main() {
    // Plain if/else, usable as either a statement or an expression.
    val a = 10
    val b = 20
    if (a < b) {
        println("${a} is less than ${b}")
    } else {
        println("${a} is not less than ${b}")
    }
    val max = if (a > b) a else b
    println("max(${a}, ${b}) = ${max}")

    for (n in 0..5) {
        println("describe(${n}) = ${describe(n)}")
    }

    // `until` excludes the upper bound; `step` skips.
    for (n in 0 until 10 step 3) {
        println("stepped: ${n}")
    }

    var total = 0
    var i = 1
    while (i <= 5) {
        total = total + i
        i = i + 1
    }
    println("1 + 2 + ... + 5 = ${total}")
}
