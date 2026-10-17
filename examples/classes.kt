// `class`/`data class`, methods, and single-superclass inheritance (`open`/`override`).
//
// Transpile + run:
//   cli/build/install/cli/bin/cli examples/classes.kt --run

// Every constructor parameter must be `val`/`var`-prefixed — there's no way to have a plain,
// non-property constructor argument in this subset. `data class` gets a free `toString()`
// (`Point(x=1, y=2)`) when it doesn't define its own.
data class Point(val x: Int, val y: Int) {
    fun distanceFromOrigin(): Double {
        return sqrt((this.x * this.x + this.y * this.y).toDouble())
    }
}

// `open` marks a class/method/property as overridable; a subclass's `: Base()` heritage clause
// compiles to Lua's standard two-link metatable prototype chain, so an unoverridden member
// (here, `legs`) is inherited for free.
open class Animal(val name: String) {
    open var legs: Int = 4

    open fun speak(): String = "..."

    // Inside a method, a call to another member always needs an explicit `this.` — there's no
    // implicit-receiver resolution the way real Kotlin has.
    fun describe(): String = "${this.name} (${this.legs} legs) says ${this.speak()}"
}

// Hard-codes the name it passes up to `Animal(...)` rather than taking its own constructor
// parameter — every primary-constructor parameter has to be `val`/`var`-prefixed in this
// subset (no plain pass-through params), and redeclaring `name` here would just shadow the
// inherited one for no reason. Keeps this example to the point; a real subclass would of
// course want its own constructor parameters for whatever *it* adds.
class Dog : Animal("Rex") {
    override fun speak(): String = "Woof!"
}

class Bird : Animal("Pip") {
    override var legs: Int = 2
    override fun speak(): String = "Tweet!"
}

fun main() {
    val p = Point(3, 4)
    println("${p} is ${p.distanceFromOrigin()} units from the origin")

    // Each `val` here is a direct `ClassName(...)` constructor call — one of the few shapes
    // kt2lua statically tracks well enough to know `.describe()`/`.speak()` need a colon-call,
    // not a plain dot-call. A `listOf(dog, bird)` mixing *different* classes together would
    // lose that tracking (see the root README's "Class support" section) — called directly
    // here instead, to keep this example simple and crash-free.
    val dog = Dog()
    val bird = Bird()
    println(dog.describe())
    println(bird.describe())
}
