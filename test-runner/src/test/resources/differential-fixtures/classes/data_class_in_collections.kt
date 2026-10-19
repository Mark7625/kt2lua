data class Item(val name: String, val price: Int)

fun main() {
    val items = listOf(Item("apple", 3), Item("pear", 5), Item("fig", 4))
    println(items.map { it.name })
    println(items.filter { it.price > 3 }.map { it.name })
    println(items.sortedBy { it.price }.map { it.name })
    println(items.sumOf { it.price })
    println(items.maxByOrNull { it.price })
    println(items.first())
    println(items.contains(Item("pear", 5)))
    println(items.indexOf(Item("fig", 4)))

    val unique = setOf(Item("a", 1), Item("a", 1), Item("b", 2))
    println(unique.size)

    for (item in items) {
        println("${item.name}:${item.price}")
    }

    val byName = items.associateBy { it.name }
    println(byName["pear"])

    val (name, price) = items[0]
    println(name)
    println(price)
}
