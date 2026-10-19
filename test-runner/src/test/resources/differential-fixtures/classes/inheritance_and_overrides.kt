open class Animal(val name: String) {
    open fun sound(): String = "..."
    fun describe(): String = "$name says ${sound()}"
}

class Dog : Animal("Rex") {
    override fun sound(): String = "Woof"
}

class Cat : Animal("Tom") {
    override fun sound(): String = "Meow"
}

fun main() {
    val dog: Animal = Dog()
    val cat: Animal = Cat()
    println(dog.describe())
    println(cat.describe())
}
