// The smallest useful kt2lua program: a function, a string template, and `println`.
//
// Transpile + run:
//   cli/build/install/cli/bin/cli examples/hello.kt --run

fun greet(name: String): String {
    return "Hello, ${name}!"
}

fun main() {
    println(greet("world"))

    val x = 6
    val y = 7
    println("${x} * ${y} = ${x * y}")
}
