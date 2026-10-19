fun greet(name: String, greeting: String = "Hello"): String = "$greeting, $name"

fun box(width: Int, height: Int = 2, depth: Int = 3): Int = width * height * depth

class Logger(val prefix: String) {
    fun log(message: String, level: Int = 1): String = "$prefix[$level] $message"
}

fun main() {
    println(greet("Ann"))
    println(greet("Bob", "Hi"))
    println(greet(name = "Cy"))
    println(greet(greeting = "Yo", name = "Di"))

    println(box(1))
    println(box(1, 5))
    println(box(1, 5, 7))
    println(box(2, depth = 10))
    println(box(width = 4, depth = 2))

    val logger = Logger("app")
    println(logger.log("started"))
    println(logger.log("failed", 3))
}
