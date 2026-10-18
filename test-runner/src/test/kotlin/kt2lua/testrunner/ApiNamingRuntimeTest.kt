package kt2lua.testrunner

import kotlin.test.Test
import kotlin.test.assertEquals
import kt2lua.interpreter.interpret
import kt2lua.transpiler.LuaBindings
import kt2lua.transpiler.transpile

class ApiNamingRuntimeTest {
    @Test fun `Kotlin API names and data records preserve the Lua host contract`() {
        val bindings = LuaBindings(mapOf(
            "symbol.api.HintArrowOptions" to "HintArrowOptions",
            "lua.HintArrowOptions" to "HintArrowOptions",
            "data.HintArrowOptions" to "true",
            "record.HintArrowOptions" to "name,npcUid,arrowId,note",
            "property.HintArrowOptions#name" to "",
            "property.HintArrowOptions#npcUid" to "",
            "propertyName.HintArrowOptions#npcUid" to "npcUID",
            "propertyName.HintArrowOptions#arrowId" to "arrowID",
            "path.HintList.addArrow" to "HintList.AddArrow",
            "path.ui.interfaces" to "ui.Interfaces",
            "global.ui.interfaces" to "InterfaceManager",
            "lua.InterfaceManager" to "InterfaceManager",
            "lua.Component" to "Component",
            "method.InterfaceManager#getComponent" to "colon|Component",
            "methodName.InterfaceManager#getComponent" to "GetComponent",
            "method.Component#destroy" to "colon|",
            "methodName.Component#destroy" to "Destroy"
        ))
        val code = transpile("""
            import api.HintArrowOptions
            fun main() {
                val original = HintArrowOptions("guide", npcUid = 12, arrowId = 7, note = "original")
                HintList.addArrow(original)
                val changed = original.copy(npcUid = 34, note = null)
                HintList.addArrow(changed)
                println(original.npcUid)
                println(changed.component2())
                changed.npcUid = 99
                println(changed.npcUid)
                ui.interfaces?.getComponent(42)?.destroy()
            }
        """.trimIndent(), bindings = bindings).code
        val host = """
            local arrows = 0
            HintList = {AddArrow = function(options)
                arrows = arrows + 1
                assert(options.name == "guide" and options.arrowID == 7)
                assert(options.npcUid == nil and options.arrowId == nil)
                if arrows == 1 then assert(options.npcUID == 12 and options.note == "original") end
                if arrows == 2 then assert(options.npcUID == 34 and options.note == nil) end
            end}
            local component = {}
            component.Destroy = function(self) assert(self == component); print("destroyed") end
            local manager = {}
            manager.GetComponent = function(self, id) assert(self == manager and id == 42); return component end
            ui = {Interfaces = manager}
        """.trimIndent()
        val output = StringBuilder()
        interpret(host + "\n" + code) { output.append(it) }
        assertEquals("12\n34\n99\ndestroyed", output.toString().trim())
    }
}
