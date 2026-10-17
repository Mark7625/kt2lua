class LoudGreeter(val who: String) : Greeter(who)

fun main() {
    val g = LoudGreeter("World")
    println(g.greet())
}
