val items = listOf(10, 20, 30)

private fun helper(x: Int): Int {
    return x * 2
}

fun sum(): Int {
    var total = 0
    for (i in 0 until items.size) {
        total = total + helper(i)
    }
    return total
}

fun describe(label: String): String {
    return "${label} has ${items.size} items"
}
