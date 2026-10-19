fun describe(xs: List<Int?>): Int = xs.filterNotNull().sum()

fun main() {
    // a List literal containing nulls, passed directly as a call argument (not pre-assigned to a
    // val) - a separate codegen path from `val xs = listOf(...)` that needs the same null-safety.
    println(describe(listOf(1, null, 3, null, 5)))
}
