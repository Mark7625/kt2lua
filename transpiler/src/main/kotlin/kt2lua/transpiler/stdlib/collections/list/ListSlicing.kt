package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitTakeStatements(resultVar: String, receiverStr: String, countStr: String) {
    line("local $resultVar = {}")
    line("for __i = 1, math.min(($countStr), #$receiverStr) do table.insert($resultVar, $receiverStr[__i]) end")
}

fun CollectionLoweringContext.emitDropStatements(resultVar: String, receiverStr: String, countStr: String) {
    line("local $resultVar = {}")
    line("for __i = ($countStr) + 1, #$receiverStr do table.insert($resultVar, $receiverStr[__i]) end")
}

fun CollectionLoweringContext.emitTakeLastStatements(resultVar: String, receiverStr: String, countStr: String) {
    line("local $resultVar = {}")
    line("for __i = math.max(1, #$receiverStr - ($countStr) + 1), #$receiverStr do table.insert($resultVar, $receiverStr[__i]) end")
}

fun CollectionLoweringContext.emitDropLastStatements(resultVar: String, receiverStr: String, countStr: String) {
    line("local $resultVar = {}")
    line("for __i = 1, #$receiverStr - ($countStr) do table.insert($resultVar, $receiverStr[__i]) end")
}

fun CollectionLoweringContext.emitTakeWhileStatements(resultVar: String, receiverStr: String, callback: Expression) {
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if not ($it) then break end; table.insert($resultVar, $paramName)" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if not $fnVar(__v) then break end")
        line("table.insert($resultVar, __v)")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitDropWhileStatements(resultVar: String, receiverStr: String, callback: Expression) {
    val droppingVar = nextTemp("__dropping")
    line("local $resultVar = {}")
    line("local $droppingVar = true")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) {
                    "if $droppingVar then if not ($it) then $droppingVar = false end end; if not $droppingVar then table.insert($resultVar, $paramName) end"
                }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if $droppingVar then if not $fnVar(__v) then $droppingVar = false end end")
        line("if not $droppingVar then table.insert($resultVar, __v) end")
        popIndent()
        line("end")
    }
}
