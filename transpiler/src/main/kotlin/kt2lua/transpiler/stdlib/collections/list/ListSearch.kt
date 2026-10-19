package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.collections.common.structuralEq
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitFindStatements(resultVar: String, receiverStr: String, callback: Expression, fromEnd: Boolean) {
    line("local $resultVar = nil")
    val rangeStr = if (fromEnd) "#$receiverStr, 1, -1" else "1, #$receiverStr"
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for __i = $rangeStr do")
        pushIndent()
        line("local $paramName = $receiverStr[__i]")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then $resultVar = $paramName; break end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i = $rangeStr do")
        pushIndent()
        line("local __v = $receiverStr[__i]")
        line("if $fnVar(__v) then $resultVar = __v; break end")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitIndexOfStatements(resultVar: String, receiverStr: String, targetStr: String, targetExpr: Expression? = null) {
    line("local $resultVar = -1")
    val eq = targetExpr?.let { structuralEq(it, "__v", targetStr) } ?: "(__v == $targetStr)"
    line("for __i, __v in ipairs($receiverStr) do if $eq then $resultVar = __i - 1; break end end")
}

fun CollectionLoweringContext.emitIndexOfPredicateStatements(resultVar: String, receiverStr: String, callback: Expression, fromEnd: Boolean) {
    line("local $resultVar = -1")
    val rangeStr = if (fromEnd) "#$receiverStr, 1, -1" else "1, #$receiverStr"
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for __i = $rangeStr do")
        pushIndent()
        line("local $paramName = $receiverStr[__i]")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then $resultVar = __i - 1; break end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i = $rangeStr do")
        pushIndent()
        line("local __v = $receiverStr[__i]")
        line("if $fnVar(__v) then $resultVar = __i - 1; break end")
        popIndent()
        line("end")
    }
}
