package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.InRangeCondition
import kt2lua.transpiler.IsTypeCondition
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.ValueCondition
import kt2lua.transpiler.WhenCondition
import kt2lua.transpiler.WhenExpr
import kt2lua.transpiler.WhenStatement

/** `when` emission — both the statement form and the expression form ([WhenExpr]), which share
 * condition emission. */
object WhenEmitter {
    private fun luaTypeOf(type: TypeName): String = when (type) {
        TypeName.INT, TypeName.DOUBLE -> "number"
        TypeName.STRING -> "string"
        TypeName.BOOLEAN -> "boolean"
        else -> error("unreachable: IsTypeCondition is restricted to Int/Double/String/Boolean at parse time")
    }

    private fun emitWhenCondition(conditions: List<WhenCondition>, subjectVar: String?, ctx: EmitContext): String =
        conditions.joinToString(" or ") { cond ->
            when (cond) {
                is ValueCondition -> {
                    val condStr = ctx.emitExpression(cond.expr)
                    if (subjectVar != null) "$subjectVar == $condStr" else condStr
                }
                is InRangeCondition -> {
                    val startStr = ctx.emitExpression(cond.start)
                    val endStr = ctx.emitExpression(cond.end)
                    val upperOp = if (cond.inclusive) "<=" else "<"
                    "($subjectVar >= $startStr and $subjectVar $upperOp $endStr)"
                }
                is IsTypeCondition -> "(type($subjectVar) == \"${luaTypeOf(cond.type)}\")"
            }
        }

    /** Compiles to a plain if/elseif/else chain. A subject is hoisted into a `do...end`-scoped
     * local first so it's only evaluated once. */
    fun emitWhenStatement(stmt: WhenStatement, ctx: EmitContext) {
        if (stmt.branches.isEmpty()) {
            stmt.elseBranch?.body?.forEach { ctx.emitStatement(it) }
            return
        }
        val subjectVar = stmt.subject?.let { "__subj" }
        if (subjectVar != null) {
            ctx.line("do")
            ctx.pushIndent()
            ctx.line("local $subjectVar = ${ctx.emitExpression(stmt.subject)}")
        }
        stmt.branches.forEachIndexed { i, branch ->
            ctx.line("${if (i == 0) "if" else "elseif"} ${emitWhenCondition(branch.conditions, subjectVar, ctx)} then")
            ctx.pushIndent()
            ctx.types.pushScope()
            branch.body.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
            ctx.popIndent()
        }
        if (stmt.elseBranch != null) {
            ctx.line("else")
            ctx.pushIndent()
            ctx.types.pushScope()
            stmt.elseBranch.body.forEach { ctx.emitStatement(it) }
            ctx.types.popScope()
            ctx.popIndent()
        }
        ctx.line("end")
        if (subjectVar != null) {
            ctx.popIndent()
            ctx.line("end")
        }
    }

    /** An IIFE with an explicit `return` per branch, rather than `IfExpr`'s `cond and a or b`
     * idiom, since `when` commonly has 3+ branches, where that idiom's falsy-branch bug is more
     * likely to matter. */
    fun emitWhenExpr(expr: WhenExpr, ctx: EmitContext): String {
        val indentUnit = ctx.style.indentUnit
        val subjectVar = expr.subject?.let { "__subj" }
        val sb = StringBuilder("(function()\n")
        if (subjectVar != null) sb.append("${indentUnit}local $subjectVar = ${ctx.emitExpression(expr.subject)}\n")
        expr.branches.forEachIndexed { i, branch ->
            sb.append("$indentUnit${if (i == 0) "if" else "elseif"} ${emitWhenCondition(branch.conditions, subjectVar, ctx)} then\n")
            sb.append("${indentUnit}${indentUnit}return ${ctx.emitExpression(branch.result)}\n")
        }
        if (expr.branches.isEmpty()) {
            sb.append("${indentUnit}return ${ctx.emitExpression(expr.elseResult)}\n")
        } else {
            sb.append("${indentUnit}else\n")
            sb.append("${indentUnit}${indentUnit}return ${ctx.emitExpression(expr.elseResult)}\n")
            sb.append("${indentUnit}end\n")
        }
        sb.append("end)()")
        return sb.toString()
    }
}
