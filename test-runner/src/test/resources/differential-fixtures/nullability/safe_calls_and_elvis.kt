class Holder(val value: String?)

fun main() {
    val present: Holder? = Holder("hi")
    val absent: Holder? = null

    println(present?.value)
    println(absent?.value)
    println(present?.value?.length)

    println(present?.value ?: "default")
    println(absent?.value ?: "default")

    val numbers: List<Int?> = listOf(1, null, 3, null, 5)
    println(numbers.filterNotNull())
    println(numbers.filterNotNull().sum())
}
