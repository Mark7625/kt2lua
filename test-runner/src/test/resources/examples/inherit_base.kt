open class Greeter(val name: String) {
    open fun greet(): String = "Hello, ${this.name}"
}
