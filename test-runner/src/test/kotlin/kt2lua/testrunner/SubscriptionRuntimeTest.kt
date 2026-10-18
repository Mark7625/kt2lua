package kt2lua.testrunner

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kt2lua.interpreter.DirectoryModuleLoader
import kt2lua.interpreter.interpret
import kt2lua.transpiler.LuaBindings
import kt2lua.transpiler.ProjectFile
import kt2lua.transpiler.transpileProject

class SubscriptionRuntimeTest {
    @Test fun `inherited subscriptions forward consuming results and clean up every lifecycle`() {
        val source = """
            open class PluginModule {
                private var detach = {}
                open fun bindSubscriptions(): Unsubscribe = {}
                fun initialize() { detach(); detach = bindSubscriptions() }
                fun stop() { detach() }
            }
            open class ParentSubscriber : PluginModule() {
                var seen = 0
                @Subscribe fun ready(event: ReadyEvent) { seen += event.amount }
            }
            class ChildSubscriber : ParentSubscriber() {
                @Subscribe("KeyDown") fun key(event: KeyEvent): Boolean = event.consume
                @Subscribe fun missing(event: MissingEvent) {}
            }
        """.trimIndent()
        val bindings = LuaBindings(mapOf(
            "event.ReadyEvent#Event.Ready" to "normal",
            "event.KeyEvent#Event.KeyDown" to "consumable",
            "event.MissingEvent#Event.Missing" to "normal"
        ))
        val directory = Files.createTempDirectory("kt2lua-subscriptions-").toFile()
        val result = transpileProject(listOf(ProjectFile("subscriber", source)), bindings = bindings)
        directory.resolve("subscriber.lua").writeText(result.files.getValue("subscriber"))
        directory.resolve("rs3-api-extended.lua").writeText("""
            local nextID = 0
            return {on = function(source, handler)
                nextID = nextID + 1
                local key = tostring(nextID)
                source.Subscribe(key, handler)
                local active = true
                return function()
                    if active then active = false; source.Unsubscribe(key) end
                end
            end}
        """.trimIndent())
        val harness = """
            local active = 0
            local handlers = {}
            local function eventSource(name)
                return {
                    Subscribe = function(key, handler) active = active + 1; handlers[name] = handler end,
                    Unsubscribe = function(key) active = active - 1 end
                }
            end
            Event = {Ready = eventSource("ready"), KeyDown = eventSource("key")}
            require("subscriber")
            local module = ChildSubscriber.new()
            module:initialize()
            assert(active == 2, "parent and child should register, missing event should be skipped")
            handlers.ready({amount = 3})
            assert(module.seen == 3, "inherited handler must receive its module instance")
            assert(handlers.key({consume = true}) == true, "consumption result must be forwarded")
            assert(handlers.key({consume = false}) == false, "false consumption result must be forwarded")
            module:initialize()
            assert(active == 2, "reinitialization must replace subscriptions")
            module:stop()
            module:stop()
            assert(active == 0, "cleanup must be idempotent")
            Event.KeyDown.Subscribe = function() error("denied") end
            local ok = pcall(function() module:initialize() end)
            assert(not ok and active == 0, "failed child registration must clean up inherited subscriptions")
            print("passed")
        """.trimIndent()
        val output = StringBuilder()
        interpret(harness, DirectoryModuleLoader(directory)) { output.append(it) }
        assertEquals("passed", output.toString().trim())
    }
}
