package kt2lua.transpiler.emitter

import kt2lua.transpiler.CallExpr
import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.ThrowStatement
import kt2lua.transpiler.toExpression

/** `throw expr` emission -> Lua's `error(...)`. */
object ThrowEmitter {
    /** A constructor-shaped call to something that isn't a registered class (e.g.
     * `Exception("boom")`) throws just its single argument — there's no real exception
     * hierarchy to model. A declared `class` instance throws that instance value instead. */
    fun emitThrowStatement(stmt: ThrowStatement, ctx: EmitContext) {
        val thrown = stmt.expr
        val messageExpr = if (thrown is CallExpr && thrown.args.size == 1 && ctx.types.classInfo(thrown.callee) == null) {
            thrown.args[0].toExpression()
        } else {
            thrown
        }
        ctx.line("error(${ctx.emitExpression(messageExpr)})")
    }
}
