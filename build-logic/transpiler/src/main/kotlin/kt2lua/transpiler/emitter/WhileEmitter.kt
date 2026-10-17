package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.WhileStatement

/** `while (cond) { ... }` emission. */
object WhileEmitter {
    fun emitWhileStatement(stmt: WhileStatement, ctx: EmitContext) {
        ctx.line("while ${ctx.emitExpression(stmt.cond)} do")
        ctx.pushIndent()
        ctx.types.pushScope()
        stmt.body.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end")
    }
}
