fun main() {
    var sum = 0
    for (i in 1..5) {
        sum += i
    }
    println(sum)

    var i = 0
    while (i < 5) {
        i += 1
    }
    println(i)

    var total = 0
    for (n in 1..10) {
        if (n % 2 != 0) {
            total += n
        }
    }
    println(total)
}
