fun main() {
    val success = runCatching { false }
        .onSuccess { println(it) }
        .onFailure { println("unexpected") }
    println(success.isSuccess)
    println(success.getOrNull() ?: true)

    val empty = runCatching { null }
    println(empty.isSuccess)
    println(empty.getOrNull() ?: "empty")

    val failure = runCatching { throw IllegalStateException("boom") }
        .onSuccess { println("unexpected") }
        .onFailure { println("caught $it") }
    println(failure.isFailure)
    println(failure.getOrNull() ?: "fallback")

    val propagated = runCatching {
        runCatching { 1 }.onSuccess { throw IllegalStateException("callback") }
    }
    println(propagated.isFailure)
}
