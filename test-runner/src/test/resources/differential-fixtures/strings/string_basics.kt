fun shout(s: String): String = "$s!"

fun main() {
    val s = "hello"
    println(s.length)
    println("${s.length * 2}")
    println(shout(s))
    println("a$s b${s}c")
    println("sum=${1 + 2}, len=${s.length}")
    println(s == "hello")
    println(s != "world")
    println("".length)
    println("3".toInt() + 4)
    println(7.toString() == "7")
    println(1.5.toString())
    println(true.toString())

    val parts = listOf("a", "b", "c")
    var joined = ""
    for (p in parts) {
        joined = "$joined$p-"
    }
    println(joined)

    println("quote: \"x\" tab:\there")
    println("dollar: \$5")
}
