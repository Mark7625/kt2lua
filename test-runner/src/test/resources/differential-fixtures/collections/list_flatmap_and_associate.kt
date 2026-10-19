fun main() {
    val numbers = listOf(1, 2, 3)
    println(numbers.flatMap { listOf(it, it * 10) })

    val m = numbers.associate { it to it * it }
    println(m[2])
    println(m[3])
}
