package kt2lua.transpiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LuaBindingsTest {
    private val bindings = LuaBindings(mapOf(
        "symbol.api.Window" to "prettyui.Window",
        "symbol.api.window.Window" to "prettyui.Window",
        "symbol.api.TextField" to "prettyui.TextField",
        "lua.prettyui.Window" to "prettyui.Window",
        "lua.prettyui.TextField" to "prettyui.TextField",
        "lua.InterfaceManager" to "InterfaceManager",
        "lua.Component" to "Component",
        "factory.prettyui.Window" to "prettyui.Window.new",
        "method.prettyui.Window#AddTextField" to "colon|prettyui.TextField",
        "method.prettyui.Window#Close" to "colon|",
        "method.prettyui.TextField#GetText" to "colon|",
        "method.prettyui.TextField#SetText" to "colon|",
        "method.InterfaceManager#GetComponent" to "colon|Component",
        "method.Component#Destroy" to "colon|",
        "global.ui.Interfaces" to "InterfaceManager",
        "method.prettyui.Window#Observe" to "dot|"
    ))

    @Test fun `host factories methods and returned controls retain their runtime signatures`() {
        val code = transpile("""
            import api.*
            fun main() {
                val win = Window.new(parent, WindowOptions(title = "Test"))
                val field = win.AddTextField()
                val alias = field
                alias.SetText("hello")
                println("value: ${'$'}{field.GetText()}")
                win.Close()
                win.Observe()
                prettyui.KeybindField.FormatValue(keys)
                Plugin.HasPermission(permission)
            }
        """.trimIndent(), bindings = bindings).code
        assertContains(code, "prettyui.Window.new(parent, {title = \"Test\"})")
        assertContains(code, "win:AddTextField()")
        assertContains(code, "alias:SetText(\"hello\")")
        assertContains(code, "field:GetText()")
        assertContains(code, "win:Close()")
        assertContains(code, "win.Observe()")
        assertContains(code, "prettyui.KeybindField.FormatValue(keys)")
        assertContains(code, "Plugin.HasPermission(permission)")
    }

    @Test fun `qualified and aliased imports use host factory without requiring stub module`() {
        val code = transpile("""
            import api.window.Window as SettingsWindow
            fun main() {
                val first = SettingsWindow.new(parent)
                first.Close()
                val second = api.Window.new(parent)
                second.Close()
            }
        """.trimIndent(), bindings = bindings).code
        assertFalse(code.contains("require("))
        assertContains(code, "local first = prettyui.Window.new(parent)")
        assertContains(code, "local second = prettyui.Window.new(parent)")
        assertContains(code, "second:Close()")
    }

    @Test fun `native receiver types survive globals fields nullable aliases and chained returns`() {
        val code = transpile("""
            import api.*
            class Controller {
                var settings: Window? = null
                fun close() {
                    val win = this.settings
                    if (win != null) { win.Close() }
                }
            }
            fun main() {
                val manager = ui.Interfaces
                val component = manager.GetComponent(123)
                component.Destroy()
                ui.Interfaces.GetComponent(123).Destroy()
            }
        """.trimIndent(), bindings = bindings).code
        assertContains(code, "win:Close()")
        assertContains(code, "manager:GetComponent(123)")
        assertContains(code, "component:Destroy()")
        assertContains(code, "ui.Interfaces:GetComponent(123):Destroy()")
    }

    @Test fun `local classes take precedence over ambient short names`() {
        val code = transpile("""
            import api.*
            class Window { fun Close() {} }
            fun main() {
                val custom = Window()
                custom.Close()
            }
        """.trimIndent(), bindings = bindings).code
        assertContains(code, "local custom = Window.new()")
        assertContains(code, "custom:Close()")
        assertFalse(code.contains("prettyui.Window"))
    }
}
