var counter = 0

fun nextOrNull(): Int? {
    if (counter >= 3) return null
    counter += 1
    return counter
}

fun main() {
    val nat = generateSequence(1) { it + 1 }
    println(nat.filterNot { it % 2 == 0 }.take(4).toList())
    println(nat.mapIndexed { i, v -> i * v }.take(4).toList())
    println(nat.mapIndexedNotNull { i, v -> if (i % 2 == 0) v * 10 else null }.take(3).toList())
    println(nat.filterIndexed { i, v -> i % 3 == 0 }.take(3).toList())

    var seen = ""
    println(listOf(1, 2, 3).asSequence().onEachIndexed { i, v -> seen = "$seen$i:$v " }.toList())
    println(seen)

    for ((i, v) in listOf("a", "b", "c").asSequence().withIndex()) println("$i=$v")
    println(listOf("a", "b").asSequence().withIndex().map { "${it.index}${it.value}" }.toList())

    println(nat.flatMap { sequenceOf(it, it * 10) }.take(5).toList())
    println(listOf(1, 2, 3).asSequence().flatMap { listOf(it, -it) }.toList())
    println(nat.flatMapIndexed { i, v -> listOf(i, v) }.take(6).toList())
    println(sequenceOf(listOf(1, 2), listOf(3), emptyList<Int>(), listOf(4, 5)).flatten().toList())

    println(nat.zip(nat.map { it * it }).take(3).toList())
    println(nat.zip(sequenceOf("a", "b", "c")) { n, s -> "$n$s" }.toList())
    println(nat.zipWithNext().take(3).toList())
    println(nat.zipWithNext { a, b -> a * b }.take(4).toList())

    println(nat.windowed(3).take(3).toList())
    println(nat.windowed(3, 2).take(3).toList())
    println(sequenceOf(1, 2, 3, 4, 5).windowed(3, 2, partialWindows = true).toList())
    println(sequenceOf(1, 2, 3, 4, 5).windowed(2, 3).toList())
    println(sequenceOf(1, 2, 3).windowed(5).toList())
    println(nat.windowed(2) { it.sum() }.take(3).toList())
    println(nat.windowed(2).map { it.sum() }.take(3).toList())
    println(nat.chunked(3).take(2).toList())
    println(sequenceOf(1, 2, 3, 4, 5, 6, 7).chunked(3).toList())
    println(sequenceOf(1, 2, 3, 4, 5).chunked(2) { it.sum() }.toList())

    println(nat.plus(0).take(3).toList())
    println(sequenceOf(1, 2).plus(sequenceOf(3, 4)).toList())
    println(sequenceOf(1, 2).plus(listOf(3, 4)).toList())
    println((sequenceOf(1, 2) + 3).toList())
    println((sequenceOf(1, 2) + listOf(7, 8)).toList())
    println(sequenceOf(1, 2, 3, 2, 1).minus(2).toList())
    println(sequenceOf(1, 2, 3, 2, 1).minus(listOf(2, 3)).toList())
    println(sequenceOf(1, 2, 3).minus(sequenceOf(1)).toList())
    println((sequenceOf(1, 2, 3, 2) - 2).toList())
    println(sequenceOf(1, 2, 1).minusElement(1).toList())
    println(sequenceOf(1).plusElement(9).toList())

    println(nat.runningFold(0) { a, b -> a + b }.take(5).toList())
    println(nat.scan(1) { a, b -> a * b }.take(5).toList())
    println(nat.runningFoldIndexed(0) { i, a, b -> a + i * b }.take(4).toList())
    println(nat.scanIndexed(0) { i, a, b -> a + b + i }.take(4).toList())
    println(nat.runningReduce { a, b -> a + b }.take(5).toList())
    println(nat.runningReduceIndexed { i, a, b -> a + b * i }.take(4).toList())
    println(emptySequence<Int>().runningFold(7) { a, b -> a + b }.toList())

    println(sequenceOf(1, 2, 1, 3, 2).distinctBy { it % 2 }.toList())
    println(sequenceOf(1, 2).requireNoNulls().toList())
    println(sequenceOf(1, 2).constrainOnce().toList())
    val maybe: Sequence<Int>? = sequenceOf(4, 5)
    println(maybe.orEmpty().toList())
    println(sequenceOf(1, 2).ifEmpty { sequenceOf(9) }.toList())
    println(emptySequence<Int>().ifEmpty { sequenceOf(9, 8) }.toList())
    println(emptySequence<Int>().toList())

    println(generateSequence { 5 }.take(3).toList())
    println(generateSequence { nextOrNull() }.toList())
    println(generateSequence({ 10 }) { it + 5 }.take(3).toList())

    val s = sequence {
        yield(1)
        yieldAll(listOf(2, 3))
        yieldAll(sequenceOf(4))
        for (i in 5..6) yield(i)
    }
    println(s.toList())
    println(s.toList())
    println(sequence { var a = 1; while (true) { yield(a); a *= 2 } }.take(6).toList())
    println(sequence { yield("x") }.map { it + "!" }.toList())

    var trace = ""
    val lazy = sequenceOf(1, 2, 3, 4)
        .onEach { trace = "$trace[$it]" }
        .filterNot { it % 2 == 1 }
        .mapIndexed { i, v -> v * 10 + i }
    println(trace)
    println(lazy.first())
    println(trace)
}
