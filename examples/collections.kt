// `listOf`/`mapOf`, indexing, and the collection methods kt2lua knows about
// (`forEach`/`map`/`filter`/`fold`/`reduce`) — each compiles to a small runtime helper, only
// emitted when actually used.
//
// Transpile + run:
//   cli/build/install/cli/bin/cli examples/collections.kt --run

fun main() {
    val numbers = listOf(1, 2, 3, 4, 5)

    numbers.forEach { n -> println("saw ${n}") }

    val doubled = numbers.map { n -> n * 2 }
    val even = numbers.filter { n -> n % 2 == 0 }
    val sum = numbers.fold(0) { acc, n -> acc + n }
    val product = numbers.reduce { acc, n -> acc * n }

    // `doubled`/`even` print as a raw `table: 0x...` address, not their contents — a `List`
    // has no special `toString()` here (string interpolation just does `tostring(...)`), so
    // printing one directly isn't useful; `forEach` each one instead if you want to see inside.
    println("doubled: ${doubled}")
    println("even: ${even}")
    println("sum: ${sum}")
    println("product: ${product}")

    // Reading an element: a `List`-typed index gets Kotlin's 0-based -> Lua's 1-based shift
    // applied automatically.
    println("first: ${numbers[0]}, last: ${numbers[4]}")

    // `mapOf` + the infix `to` pair-builder — a `String` index is a plain key lookup, no shift.
    val scores = mapOf("alice" to 90, "bob" to 75)
    println("alice scored ${scores["alice"]}")
    println("bob scored ${scores["bob"]}")
}
