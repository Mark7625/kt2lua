class Account(var balance: Int) {
    fun deposit(n: Int) {
        balance += n
    }
}

fun main() {
    val a = Account(10)
    with(a) {
        deposit(5)
        balance += 1
    }
    println(a.balance)

    val b = Account(0).apply {
        deposit(7)
    }
    println(b.balance)

    val r = a.run { balance * 2 }
    println(r)
    println(a.let { it.balance + 1 })
}
