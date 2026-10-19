fun grade(score: Int): String = when {
    score >= 90 -> "A"
    score >= 80 -> "B"
    score >= 70 -> "C"
    else -> "F"
}

fun bucket(n: Int): String = when (n) {
    in 1..3 -> "low"
    in 4..6 -> "mid"
    in 7..9 -> "high"
    else -> "out"
}

fun kind(x: Any): String = when (x) {
    is Int -> "int"
    is String -> "string"
    is Boolean -> "boolean"
    else -> "other"
}

fun main() {
    println(grade(95))
    println(grade(85))
    println(grade(72))
    println(grade(10))

    for (n in listOf(0, 2, 5, 8, 12)) {
        println(bucket(n))
    }

    println(kind(1))
    println(kind("a"))
    println(kind(true))

    var log = ""
    for (n in 1..4) {
        when (n) {
            1 -> log = "${log}one "
            2, 3 -> {
                log = "${log}two-or-three "
            }
            else -> log = "${log}other "
        }
    }
    println(log)

    val x = 7
    val label = if (x > 5) {
        "big"
    } else {
        "small"
    }
    println(label)

    val nested = if (x < 0) "neg" else if (x == 0) "zero" else if (x < 10) "digit" else "large"
    println(nested)
}
