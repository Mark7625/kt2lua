fun main() {
    val m = mapOf("a" to 1, "b" to 2, "c" to 3)
    var total = 0
    m.forEach { total += it.value }
    println(total)

    var trace = ""
    m.forEach { (k, v) -> trace = "$trace$k=$v " }
    println(trace)

    val xs = mutableSetOf(1, 2, 3)
    xs.clear()
    println(xs.size)

    val mm = mutableMapOf("a" to 1, "b" to 2)
    mm.clear()
    println(mm.size)
}
