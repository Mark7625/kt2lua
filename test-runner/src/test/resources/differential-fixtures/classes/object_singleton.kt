object Config {
    val name = "kt2lua"
    val limit = 10
    var level = 1

    fun bump() {
        level += 1
    }

    fun label(): String = "$name/$limit"
    fun summary(): String = "$name@$level"
    fun withLevel(level: Int): Int = level * 2
}

object MathUtil {
    fun square(n: Int): Int = n * n
    fun cube(n: Int): Int = n * square(n)
    fun add(a: Int, b: Int): Int = a + b
    fun sumOfSquares(a: Int, b: Int): Int = add(square(a), square(b))
}

object Counter {
    var count = 0
    val history = mutableListOf<Int>()

    fun next(): Int {
        count += 1
        history.add(count)
        return count
    }
}

fun main() {
    println(Config.name)
    println(Config.limit)
    println(Config.label())
    println(Config.level)
    Config.bump()
    Config.bump()
    println(Config.level)
    println(Config.summary())
    println(Config.withLevel(21))
    println(Config.level)

    println(MathUtil.square(4))
    println(MathUtil.cube(3))
    println(MathUtil.add(2, 3))
    println(MathUtil.sumOfSquares(3, 4))
    println(listOf(1, 2, 3).map { MathUtil.square(it) })

    Counter.next()
    Counter.next()
    println(Counter.next())
    println(Counter.count)
    println(Counter.history)
}
