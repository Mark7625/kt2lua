fun main() {
    val xs = mutableListOf(1, 2, 3)
    xs.addAll(listOf(4, 5))
    println(xs)

    val ys = mutableListOf(1, 2, 3, 4, 5, 6)
    ys.removeAll { it % 2 == 0 }
    println(ys)

    val zs = mutableListOf(1, 2, 3, 4, 5, 6)
    zs.retainAll { it % 2 == 0 }
    println(zs)

    val ws = mutableListOf(1, 2, 3, 4, 5)
    ws.retainAll(listOf(2, 4))
    println(ws)

    val vs = mutableListOf(3, 1, 2)
    vs.sortByDescending { it }
    println(vs)

    val us = mutableListOf(3, 1, 2)
    us.sortBy { -it }
    println(us)
}
