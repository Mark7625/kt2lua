package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.IfStatement

/** `if`/`else` statement emission. The expression form (`IfExpr`) needs no feature of its own —
 * it's a single `cond and a or b` expression, emitted inline by the central [kt2lua.transpiler.Emitter]. */
object IfEmitter {
    fun emitIfStatement(stmt: IfStatement, ctx: EmitContext) {
        ctx.line("if ${ctx.emitExpression(stmt.cond)} then")
        ctx.pushIndent()
        ctx.types.pushScope()
        stmt.thenBranch.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        if (stmt.elseBranch != null) {
            ctx.line("else")
            ctx.pushIndent()
            ctx.types.pushScope()
            stmt.elseBranch.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
            ctx.popIndent()
        }
        ctx.line("end")
    }
}
