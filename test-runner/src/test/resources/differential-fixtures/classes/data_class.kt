data class Point(val x: Int, val y: Int)

fun main() {
    val p1 = Point(1, 2)
    val p2 = Point(1, 2)
    val p3 = Point(3, 4)
    println(p1 == p2)
    println(p1 == p3)
    println(p1)

    val moved = p1.copy(x = 10)
    println(moved)
    println(p1)

    val (x, y) = p1
    println(x)
    println(y)
}
