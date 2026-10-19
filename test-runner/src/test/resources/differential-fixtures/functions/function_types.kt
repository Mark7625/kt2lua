fun applyTwice(f: (Int) -> Int, x: Int): Int = f(f(x))

fun makeAdder(n: Int): (Int) -> Int = { x -> x + n }

fun makeCounter(): () -> Int {
    var count = 0
    return {
        count += 1
        count
    }
}

fun double(n: Int): Int = n * 2

fun main() {
    println(applyTwice({ it + 3 }, 1))
    println(applyTwice(::double, 3))
    val addFive = makeAdder(5)
    println(addFive(10))
    val counter = makeCounter()
    counter()
    counter()
    println(counter())
    println(makeAdder(1)(1))
    println(addFive.invoke(1))
}
