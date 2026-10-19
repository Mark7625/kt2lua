fun main() {
    val numbers: List<Int?> = listOf(1, null, 3, null, 5)

    // printing a list with embedded nulls directly
    println(numbers)

    // a transform that KEEPS null elements in its own output list
    val kept = numbers.filter { true }
    println(kept)
    println(kept.size)

    val mapped = numbers.map { it }
    println(mapped)

    // search/membership against a null element
    println(numbers.contains(null))
    println(null in numbers)
    println(numbers.indexOf(null))
    println(numbers.get(1))
    println(numbers[3])

    // forEach sees real null, not a sentinel object
    var nullCount = 0
    numbers.forEach { if (it == null) nullCount += 1 }
    println(nullCount)

    // a plain `for (x in ...)` loop sees real null too
    var forLoopNullCount = 0
    for (x in numbers) {
        if (x == null) forLoopNullCount += 1
    }
    println(forLoopNullCount)

    // mutable list: add/remove a literal null
    val xs: MutableList<Int?> = mutableListOf(1, 2, 3)
    xs.add(null)
    println(xs)
    println(xs.size)
    xs.remove(null)
    println(xs)
    println(xs.size)
}
