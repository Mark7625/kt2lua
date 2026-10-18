package kt2lua.transpiler.emitter

import kt2lua.transpiler.ClassDecl
import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.LuaBindings

/** Compile-time registration: Lua has no Kotlin annotation reflection. */
object SubscriptionEmitter {
    fun emit(decl: ClassDecl, ctx: EmitContext, bindings: LuaBindings) {
        val handlers = decl.methods.filter { it.subscription != null }
        if (handlers.isEmpty()) return
        val ancestors = generateSequence(decl.superClass) { ctx.types.classInfo(it)?.superClass }.toList()
        require("PluginModule" in ancestors) { "@Subscribe handlers must belong to a PluginModule subclass: ${decl.name}" }
        require(decl.methods.none { it.name == "bindSubscriptions" }) {
            "${decl.name} cannot define bindSubscriptions alongside @Subscribe handlers"
        }
        val registrations = handlers.map { handler ->
            require(handler.params.size == 1) { "@Subscribe ${decl.name}.${handler.name} must accept exactly one event parameter" }
            val events = bindings.events(handler.params.single().rawType)
            val requested = handler.subscription!!.event?.let { if (it.startsWith("Event.")) it else "Event.$it" }
            val source = if (requested != null) {
                require(requested in events) { "$requested does not emit ${handler.params.single().rawType} for ${handler.name}" }
                requested
            } else {
                require(events.size == 1) {
                    "Cannot infer @Subscribe event for ${handler.params.single().rawType} in ${handler.name}: " +
                        if (events.isEmpty()) "no event binding found; load core --bindings metadata"
                        else "choose one with @Subscribe(\"EventName\"): ${events.keys.sorted().joinToString()}"
                }
                events.keys.single()
            }
            require(events[source] != "consumable" || handler.rawReturnType == "Boolean") {
                "@Subscribe ${handler.name} must return Boolean to handle consumable event $source"
            }
            handler to source
        }
        ctx.line("")
        ctx.line("function ${decl.name}:bindSubscriptions()")
        ctx.pushIndent()
        ctx.line("local __ext = require(\"rs3-api-extended\")")
        ctx.line("local __cleanup = {}")
        ctx.line("local function __detach()")
        ctx.pushIndent()
        ctx.line("local pending = __cleanup")
        ctx.line("__cleanup = {}")
        ctx.line("for _, detach in ipairs(pending) do pcall(detach) end")
        ctx.popIndent()
        ctx.line("end")
        ctx.line("local __ok, __error = pcall(function()")
        ctx.pushIndent()
        ctx.line("local inherited = ${decl.superClass}.bindSubscriptions(self)")
        ctx.line("if inherited ~= nil then __cleanup[#__cleanup + 1] = inherited end")
        for ((index, registration) in registrations.withIndex()) {
            val (handler, source) = registration
            ctx.line("local source$index = $source")
            ctx.line("if source$index ~= nil then")
            ctx.pushIndent()
            ctx.line("local detach = __ext.on(source$index, function(event) return self:${handler.name}(event) end)")
            ctx.line("if detach ~= nil then __cleanup[#__cleanup + 1] = detach end")
            ctx.popIndent()
            ctx.line("end")
        }
        ctx.popIndent()
        ctx.line("end)")
        ctx.line("if not __ok then __detach(); error(__error) end")
        ctx.line("return __detach")
        ctx.popIndent()
        ctx.line("end")
    }
}
