import oop_lib

fun main() {
    println(oop_lib.describe("widgets"))
    println(oop_lib.sum())
    val ref = ::standalone
    println(ref())
}

private fun standalone(): Int {
    return 42
}
