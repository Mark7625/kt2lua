open class Animal(val name: String)

class Dog(name: String) : Animal(name)

class Counter(start: Int) {
    val value: Int = start * 2
}

fun main() {
    println(Dog("rex").name)
    println(Counter(4).value)
}
