open class LifecycleBase {
    val callbacks = mapOf("init" to { this.onInit() }, "tick" to { this.tick() })
    open fun onInit() {}
    open fun tick() {}
}

class LifecycleModule : LifecycleBase() {
    var ticks = 0
    override fun onInit() { println("initialized") }
    override fun tick() {
        this.ticks = this.ticks + 1
        println(this.ticks)
    }
}

fun main() {
    val first = LifecycleModule()
    val second = LifecycleModule()
    val initFirst = first.callbacks["init"]!!
    val tickFirst = first.callbacks["tick"]!!
    val initSecond = second.callbacks["init"]!!
    val tickSecond = second.callbacks["tick"]!!
    initFirst()
    tickFirst()
    tickFirst()
    initSecond()
    tickSecond()
}
