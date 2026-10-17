open class Animal(val name: String) {
    open var sound = "..."

    open fun speak(): String = "${this.name} says ${this.sound}"
}

class Dog(val nickname: String) : Animal(nickname) {
    override var sound = "woof"

    fun fetch(): String = "${this.name} fetches the ball"
}

fun main() {
    val pet = Dog("Rex")
    println(pet.speak())
    println(pet.fetch())
}
