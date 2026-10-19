class User(val name: String, val email: String?)

fun findUser(id: Int): User? = if (id == 1) User("ann", "ann@x.io") else if (id == 2) User("bob", null) else null

fun emailOrDefault(user: User?): String = user?.email ?: "none"

fun length(s: String?): Int {
    if (s == null) return -1
    return s.length
}

fun main() {
    println(findUser(1)?.name)
    println(findUser(2)?.email)
    println(findUser(3)?.name)

    println(emailOrDefault(findUser(1)))
    println(emailOrDefault(findUser(2)))
    println(emailOrDefault(findUser(3)))

    println(length("abc"))
    println(length(null))

    val maybe: String? = "value"
    println(maybe != null)
    println(maybe!!.length)

    val nothing: String? = null
    println(nothing == null)
    println(nothing ?: "fallback")

    findUser(1)?.let { println("found ${it.name}") }
    findUser(3)?.let { println("never") }

    val ids = listOf(1, 2, 3)
    println(ids.map { findUser(it)?.name ?: "?" })
    println(ids.mapNotNull { findUser(it)?.name })

    var cache: Int? = null
    println(cache ?: 0)
    cache = 5
    println(cache ?: 0)
}
