package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.TryStatement
import kt2lua.transpiler.TypeName

/** `try`/`catch`/`finally` emission. Lua has no native try/catch, so this compiles to `pcall`;
 * a missing `catch` re-raises the original error after `finally` runs. */
object TryEmitter {
    fun emitTryStatement(stmt: TryStatement, ctx: EmitContext) {
        ctx.line("local __ok, __err = pcall(function()")
        ctx.pushIndent()
        ctx.types.pushScope()
        stmt.tryBlock.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end)")
        if (stmt.catch != null) {
            ctx.line("if not __ok then")
            ctx.pushIndent()
            ctx.types.pushScope()
            ctx.types.env.declare(stmt.catch.paramName, TypeName.ANY)
            ctx.line("local ${stmt.catch.paramName} = __err")
            stmt.catch.body.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
            ctx.popIndent()
            ctx.line("end")
        }
        if (stmt.finallyBlock != null) {
            ctx.types.pushScope()
            stmt.finallyBlock.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
        }
        if (stmt.catch == null) ctx.line("if not __ok then error(__err) end")
    }
}
