package kt2lua.transpiler.emitter

import kt2lua.transpiler.ClassDecl
import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.FunctionDecl

/**
 * `class Name(...) [: Super(args)] { ... }` emission — see [ClassDecl]'s doc comment (in
 * `Ast.kt`) for the exact Lua shape: the metatable-based "class" idiom with a `.new(...)`
 * constructor and colon-method sugar for each method.
 */
object ClassEmitter {
    fun emitClassDecl(decl: ClassDecl, ctx: EmitContext) {
        if (decl.superClass != null) {
            ctx.line("${decl.name} = setmetatable({}, { __index = ${decl.superClass} })")
        } else {
            ctx.line("${decl.name} = {}")
        }
        ctx.line("${decl.name}.__index = ${decl.name}")
        ctx.line("function ${decl.name}.new(${decl.params.joinToString(", ") { it.name }})")
        ctx.pushIndent()
        if (decl.superClass != null) {
            ctx.line("local self = setmetatable(${decl.superClass}.new(${ctx.emitArgList(decl.superArgs)}), ${decl.name})")
        } else {
            ctx.line("local self = setmetatable({}, ${decl.name})")
        }
        ctx.types.pushScope()
        ctx.types.declareInstance("self", decl.name)
        for (p in decl.params) ctx.line("self.${p.name} = ${p.name}")
        for (prop in decl.properties) ctx.line("self.${prop.name} = ${ctx.emitExpression(prop.init)}")
        ctx.types.popScope()
        ctx.line("return self")
        ctx.popIndent()
        ctx.line("end")
        if (decl.isData && decl.methods.none { it.name == "toString" }) emitDataClassToString(decl, ctx)
        for (m in decl.methods) {
            if (ctx.style.blankLineBeforeDeclarations) ctx.line("")
            emitClassMethod(decl.name, m, ctx)
        }
    }

    /** Mirrors Kotlin's auto-generated `data class` `toString()` (`Point(x=1, y=2)`) as a Lua
     * `__tostring` metamethod, so `tostring(instance)`/string-template interpolation of an
     * instance produces something useful instead of Lua's default `table: 0x...`. */
    private fun emitDataClassToString(decl: ClassDecl, ctx: EmitContext) {
        val fields = decl.params.joinToString(", ") { "${it.name}=\" .. tostring(self.${it.name}) .. \"" }
        ctx.line("${decl.name}.__tostring = function(self) return \"${decl.name}($fields)\" end")
    }

    private fun emitClassMethod(className: String, fn: FunctionDecl, ctx: EmitContext) {
        ctx.types.pushScope()
        ctx.types.declareInstance("self", className)
        fn.params.forEach {
            ctx.types.env.declare(it.name, it.type)
            ctx.types.declareInstance(it.name, it.rawType)
        }
        ctx.line("function $className:${fn.name}(${ctx.paramList(fn)})")
        ctx.pushIndent()
        ctx.emitFunctionBody(fn)
        ctx.popIndent()
        ctx.line("end")
        ctx.types.popScope()
    }
}
