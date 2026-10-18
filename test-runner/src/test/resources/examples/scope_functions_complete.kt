class ScopeBox(var value: Int) {
    fun add(amount: Int) { value += amount }
    fun read(): Int = value
}

fun main() {
    val box = ScopeBox(1)
    val same = box.apply { add(1); -1 }.also { it.add(1); -1 }
    println(same == box)
    println(box.read())

    val fromLet = box.let { original ->
        val copy = ScopeBox(original.value + 2)
        copy
    }
    println(fromLet.read())
    println(box.run { add(1); ScopeBox(value + 3) }.read())
    println(with(box) { ScopeBox(value + 4) }.read())
    println(run { val initial = 8; ScopeBox(initial + 1) }.read())
    println(run { if (true) ScopeBox(10) else ScopeBox(0) }.read())

    box.let {
        println(run { it.read() })
        println(runCatching { it.read() }.getOrNull())
    }
    println("Hello".run { length })
    println(with(listOf("one", "two")) { size })
    "reference".let(::println)

    val missing: ScopeBox? = null
    missing?.apply { println("unexpected") }
    missing?.also { println("unexpected") }
    missing?.let { println("unexpected") }
    missing?.run { println("unexpected") }
    println(missing?.let { it.read() } ?: "missing")

    println(box.takeIf { it.value == 4 }?.read())
    println(box.takeUnless { it.value == 4 }?.read() ?: "filtered")
    println(false.takeIf { true } ?: true)
    println(false.takeUnless { false } ?: true)
    println(2.takeIf { it > 1 }?.let { it + 1 })
}
