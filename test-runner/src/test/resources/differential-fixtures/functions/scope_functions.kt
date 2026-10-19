class Box(var value: Int)

fun main() {
    val x = 5
    val y = x.let { it * 2 }
    println(y)

    val box = Box(1)
    box.also { it.value = 99 }
    println(box.value)

    val result = Box(10).apply { value = value + 5 }
    println(result.value)

    val z = 7
    println(z.takeIf { it > 5 })

    var trace = ""
    val xs = listOf(1, 2, 3)
    println(xs.also { it.forEach { v -> trace = "$trace$v " } })
    println(trace)
}
