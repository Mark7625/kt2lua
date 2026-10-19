fun letters(r: CharRange): String {
    var out = ""
    for (c in r) out = "$out$c"
    return out
}

fun grade(c: Char): String = when (c) {
    in 'a'..'c' -> "early"
    in 'd'..'f' -> "middle"
    else -> "late"
}

fun main() {
    val r: CharRange = 'a'..'e'
    println(r.toList())
    println(r.first)
    println(r.last)
    println(letters(r))
    println(letters('x'..'z'))
    println(('a'..'e' step 2).toList())
    println(('e' downTo 'a').toList())
    println(('a'..<'d').toList())
    println('c' in 'a'..'f')
    println('z' in 'a'..'f')
    println('m' !in 'a'..'f')
    println(r.contains('b'))
    println(('a'..'d').map { it.code })
    println(('a'..'d').map { it.toString() + "!" })

    var line = ""
    for (c in 'a'..'e') line = "$line$c"
    println(line)
    for (c in 'a'..'e' step 2) print(c)
    println()
    for (c in 'c' downTo 'a') print(c)
    println()

    println('a' + 1)
    println('e' - 1)
    println('e' - 'a')
    println('b'.code)
    println(100.toChar())
    println((97 + 2).toChar())
    println('a' < 'b')
    println('a' == 'a')
    println("tab:\t|".length)
    println('\n'.code)
    println('\u0041')
    println('\'')

    println(grade('b'))
    println(grade('e'))
    println(grade('z'))
    val c: Char = 'q'
    println(c)
    println("$c-$c")
    println(listOf('x', 'y', 'z'))
}
