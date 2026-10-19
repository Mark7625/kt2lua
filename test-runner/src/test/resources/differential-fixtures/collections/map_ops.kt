fun main() {
    val m = mutableMapOf("a" to 1, "b" to 2)
    m["c"] = 3
    m.put("d", 4)
    println(m["a"])
    println(m["d"])
    println(m["missing"])
    println(m.containsKey("b"))
    println(m.containsValue(2))
    println(m.getOrDefault("missing", -1))
    m.remove("a")
    println(m.containsKey("a"))
    println(m.size)

    val fixed = mapOf(1 to "one", 2 to "two")
    println(fixed.map { it.value }.sorted())
    println(fixed.filter { it.key > 1 }.size)
    println(fixed.map { (k, v) -> "$k=$v" }.sorted())
    println(fixed.filter { (k, v) -> k > 1 }.size)

    val hm = hashMapOf("x" to 1, "y" to 2)
    println(hm.size)
    println(hm["x"])

    println(emptyMap<String, Int>().size)
}
