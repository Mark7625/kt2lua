class GuardSource {
    var calls = 0
    fun next(): String? {
        calls += 1
        return null
    }
}

fun guardValue(input: String?): String {
    val value = input ?: return "missing"
    return value
}

fun guardFalse(input: Boolean?): Boolean {
    val value = input ?: return true
    return value
}

fun guardSideEffect(source: GuardSource): String {
    val value = source.next() ?: return "empty"
    return value
}

fun guardStatement(input: String?) {
    input ?: return
    println("present")
}

fun main() {
    println(guardValue(null))
    println(guardValue("ready"))
    println(guardFalse(false))
    val source = GuardSource()
    println(guardSideEffect(source))
    println(source.calls)
    guardStatement(null)
    guardStatement("ready")
}
