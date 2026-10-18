fun returnOnItsOwnLine() {
    if (true) return
    println("unreachable")
}

fun main() {
    returnOnItsOwnLine()
    println("continued")
}
