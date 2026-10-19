class Person(val name: String, val age: Int)

fun greet(name: String): String = "Hello, " + name + "!"

fun main() {
    val name = "world"
    println("Hello " + name)
    var acc = ""
    acc = acc + "a"
    acc += "b"
    acc += 1
    acc += 2.5
    println(acc)
    println(greet("Kotlin"))
    println("n=" + 5)
    println("pi=" + 3.5)
    println("flag=" + true)

    val p = Person("Ann", 30)
    println("name: " + p.name + ", age: " + p.age)

    var line = ""
    for (i in 1..3) {
        line = line + i + ","
    }
    println(line)
    println(listOf("a", "b").map { it + "!" })
}
