fun main() {
    val m = mapOf("a" to 1, "b" to 2, "c" to 3)
    println(m.keys)
    println(m.values)
    println(m.count())
    println(m.size)
    println(m.isEmpty())
    println(m.keys.size)
    println(m.values.sum())
    for (k in m.keys) {
        println("$k=${m[k]}")
    }
    println(m.containsKey("a") && !m.containsKey("z"))
    println(m.entries.size)
}
