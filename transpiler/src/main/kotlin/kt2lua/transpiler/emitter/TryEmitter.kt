package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TryStatement
import kt2lua.transpiler.TypeName

/** `try`/`catch`/`finally` emission. Lua has no native try/catch, so this compiles to `pcall`.
 * Both the try body and the catch body run inside a closure so `finally` always gets to run
 * (even when the catch body throws or returns); a `return` inside either closure is reported as
 * `return true, value` and replayed by the enclosing function once `finally` has run. */
object TryEmitter {
    fun emitTryStatement(stmt: TryStatement, ctx: EmitContext) {
        val outerInsideTry = ctx.insideTryClosure
        val outerReturnSeen = ctx.tryReturnSeen
        ctx.tryReturnSeen = false

        ctx.line("local __ok, __err, __val = pcall(function()")
        emitClosureBody(ctx) { stmt.tryBlock.body.forEach { ctx.emitStatement(it) } }
        ctx.line("end)")

        if (stmt.catch != null) {
            ctx.useRuntimeHelper(RuntimeHelper.EXCEPTION)
            ctx.line("if not __ok then")
            ctx.pushIndent()
            ctx.line("local __caught = __err")
            ctx.line("__ok, __err, __val = pcall(function()")
            emitClosureBody(ctx) {
                ctx.types.env.declare(stmt.catch.paramName, TypeName.ANY)
                ctx.line("local ${stmt.catch.paramName} = __exception(__caught)")
                stmt.catch.body.body.forEach { ctx.emitStatement(it) }
            }
            ctx.line("end)")
            ctx.popIndent()
            ctx.line("end")
        }

        val returnSeen = ctx.tryReturnSeen
        ctx.insideTryClosure = outerInsideTry
        if (stmt.finallyBlock != null) {
            ctx.types.pushScope()
            stmt.finallyBlock.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
        }
        ctx.line("if not __ok then error(__err, 0) end")
        if (returnSeen) {
            ctx.line("if __err == true then")
            ctx.pushIndent()
            ctx.emitStatement(kt2lua.transpiler.ReturnStatement(kt2lua.transpiler.Identifier("__val")))
            ctx.popIndent()
            ctx.line("end")
        }
        ctx.tryReturnSeen = outerReturnSeen || (returnSeen && outerInsideTry)
    }

    private fun emitClosureBody(ctx: EmitContext, body: () -> Unit) {
        ctx.pushIndent()
        ctx.types.pushScope()
        ctx.insideTryClosure = true
        body()
        ctx.types.popScope()
        ctx.popIndent()
    }
}
