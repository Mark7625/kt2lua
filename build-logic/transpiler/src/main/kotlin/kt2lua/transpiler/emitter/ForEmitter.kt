package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.ForInStatement
import kt2lua.transpiler.ForStatement
import kt2lua.transpiler.TypeName

/** `for` emission — [ForStatement] (a numeric range) and [ForInStatement] (collection
 * iteration) are two different AST nodes for the same Kotlin keyword, so both live here. */
object ForEmitter {
    fun emitForStatement(stmt: ForStatement, ctx: EmitContext) {
        val startStr = ctx.emitExpression(stmt.start)
        val limitStr = if (stmt.inclusive) ctx.emitExpression(stmt.end) else "(${ctx.emitExpression(stmt.end)} - 1)"
        val stepStr = stmt.step?.let { ctx.emitExpression(it) }
        val header = if (stepStr != null) {
            "for ${stmt.varName} = $startStr, $limitStr, $stepStr do"
        } else {
            "for ${stmt.varName} = $startStr, $limitStr do"
        }
        ctx.line(header)
        ctx.pushIndent()
        ctx.types.pushScope()
        ctx.types.env.declare(stmt.varName, TypeName.INT)
        stmt.body.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end")
    }

    fun emitForInStatement(stmt: ForInStatement, ctx: EmitContext) {
        ctx.line("for _, ${stmt.varName} in ipairs(${ctx.emitExpression(stmt.iterable)}) do")
        ctx.pushIndent()
        ctx.types.pushScope()
        ctx.types.env.declare(stmt.varName, TypeName.ANY)
        stmt.body.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end")
    }
}
