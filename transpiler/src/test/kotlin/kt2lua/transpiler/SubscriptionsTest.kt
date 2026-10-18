package kt2lua.transpiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SubscriptionsTest {
    private val bindings = LuaBindings(mapOf(
        "event.StateEvent#Event.StateChanged" to "normal",
        "event.KeyEvent#Event.KeyDown" to "consumable",
        "event.KeyEvent#Event.KeyUp" to "consumable"
    ))
    private fun compile(handler: String, superClass: String = "PluginModule") = transpile("""
        open class PluginModule {
            open fun bindSubscriptions() = {}
        }
        class Subscriber : $superClass() { $handler }
    """.trimIndent(), bindings = bindings).code

    @Test fun `event type generates lifecycle registration and method receiver`() {
        val code = compile("@Subscribe fun stateChanged(event: StateEvent) {}")
        assertContains(code, "function Subscriber:bindSubscriptions()")
        assertContains(code, "local source0 = Event.StateChanged")
        assertContains(code, "return self:stateChanged(event)")
        assertContains(code, "PluginModule.bindSubscriptions(self)")
        assertContains(code, "if source0 ~= nil then")
        assertContains(code, "for _, detach in ipairs(pending) do pcall(detach) end")
        assertContains(code, "if not __ok then __detach(); error(__error) end")
    }

    @Test fun `ambiguous event types require an explicit source`() {
        val error = assertFailsWith<IllegalArgumentException> {
            compile("@Subscribe fun keyChanged(event: KeyEvent): Boolean = false")
        }
        assertContains(error.message!!, "Event.KeyDown, Event.KeyUp")
        val code = compile("@Subscribe(event = \"KeyDown\") fun keyChanged(event: KeyEvent): Boolean = true")
        assertContains(code, "local source0 = Event.KeyDown")
        assertContains(code, "return self:keyChanged(event)")
    }

    @Test fun `invalid event source consuming return type and signatures fail clearly`() {
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe fun bad() {}") }
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe fun bad(a: StateEvent, b: StateEvent) {}") }
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe fun bad(event: UnknownEvent) {}") }
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe(\"KeyDown\") fun bad(event: StateEvent) {}") }
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe(\"KeyDown\") fun bad(event: KeyEvent) {}") }
        assertFailsWith<IllegalArgumentException> { compile("@Subscribe fun bad(event: StateEvent) {}", "Unrelated") }
    }
}
