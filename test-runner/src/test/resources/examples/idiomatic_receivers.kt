class ScopedWidget(var label: String) {
    private var changes = 0

    fun rename(value: String) {
        label = value
        changes += 1
    }

    fun describe(): String = "$label:$changes"
}

class ScopedModule {
    private var widget: ScopedWidget? = null
    private var events = 0

    private fun record() { events += 1 }
    private fun unexpected(): String {
        events += 100
        return "unexpected"
    }

    private fun make(): ScopedWidget = ScopedWidget("start").also { widget = it }.apply {
        rename("ready")
        record()
        val label = "local"
        println(label)
        println(this.label)
        ScopedWidget("nested").apply {
            rename("inner")
            record()
            println(describe())
        }
        println(describe())
    }

    fun exercise() {
        val first = make()
        val alias = widget
        alias?.rename("changed")
        println(first.describe())
        widget?.apply {
            rename("safe")
            record()
        }
        println(events)
        widget = null
        widget?.rename(unexpected())
        println(widget?.describe() ?: "none")
        println(events)
    }
}

fun main() { ScopedModule().exercise() }
