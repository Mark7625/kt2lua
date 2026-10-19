fun main() {
    var acc = ""
    for (i in 1..3) {
        acc = "$acc$i"
    }
    println(acc)

    acc = ""
    for (i in 0 until 4) {
        acc = "$acc$i"
    }
    println(acc)

    acc = ""
    for (i in 1..10 step 3) {
        acc = "$acc$i,"
    }
    println(acc)

    acc = ""
    for (i in 5 downTo 1) {
        acc = "$acc$i"
    }
    println(acc)

    acc = ""
    for (i in 10 downTo 1 step 4) acc = "$acc$i,"
    println(acc)

    acc = ""
    for (i in 1..3) acc = "$acc$i"
    println(acc)

    var w = 0
    while (w < 3) w += 1
    println(w)

    var product = 1
    for (i in 1..4) {
        for (j in 1..i) {
            product += j
        }
    }
    println(product)

    var n = 10
    var steps = 0
    while (n > 1) {
        n = if (n % 2 == 0) n / 2 else 3 * n + 1
        steps += 1
    }
    println(steps)

    val m = mapOf("a" to 1, "b" to 2)
    var total = 0
    for ((k, v) in m) {
        total += v
        println("$k->$v")
    }
    println(total)

    var empty = 0
    for (i in 3..1) {
        empty += 1
    }
    println(empty)

    for (word in listOf("x", "y", "z")) {
        print(word)
    }
    println()
}
