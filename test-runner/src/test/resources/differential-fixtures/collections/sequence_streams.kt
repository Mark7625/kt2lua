import kotlin.streams.asSequence
import kotlin.streams.asStream

fun main() {
    val stream = listOf(1, 2, 3, 4).asSequence().asStream()
    println(stream.asSequence().map { it * 2 }.toList())
    println(sequenceOf(5, 6, 7).asStream().asSequence().toList())
    println(listOf(1, 2, 3).asSequence().filter { it > 1 }.asStream().asSequence().sum())
    println(generateSequence(1) { it * 3 }.take(4).asStream().asSequence().toList())
}
