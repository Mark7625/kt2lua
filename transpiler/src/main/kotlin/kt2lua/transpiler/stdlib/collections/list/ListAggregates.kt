package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitAnyAllNoneStatements(resultVar: String, receiverStr: String, callback: Expression, mode: String) {
    val initial = if (mode == "any") "false" else "true"
    line("local $resultVar = $initial")
    val sink: (String) -> String = when (mode) {
        "any" -> { v -> "if $v then $resultVar = true; break end" }
        "all" -> { v -> "if not ($v) then $resultVar = false; break end" }
        else -> { v -> "if $v then $resultVar = false; break end" }
    }
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("$paramName = __nilDecode($paramName)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt -> if (i == stmts.lastIndex) emitLambdaResult(stmt, sink) else emitStatement(stmt) }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line(sink("$fnVar(__nilDecode(__v))"))
        popIndent()
        line("end")
    }
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
}

fun CollectionLoweringContext.emitCountStatements(resultVar: String, receiverStr: String, callback: Expression?) {
    if (callback == null) {
        line("local $resultVar = #$receiverStr")
        return
    }
    line("local $resultVar = 0")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("$paramName = __nilDecode($paramName)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then $resultVar = $resultVar + 1 end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if $fnVar(__nilDecode(__v)) then $resultVar = $resultVar + 1 end")
        popIndent()
        line("end")
    }
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
}

fun CollectionLoweringContext.emitSumStatements(resultVar: String, receiverStr: String, selector: Expression?) {
    line("local $resultVar = 0")
    when {
        selector == null -> line("for _, __v in ipairs($receiverStr) do $resultVar = $resultVar + __v end")
        selector is LambdaExpr -> {
            val paramName = selector.params.firstOrNull() ?: "it"
            pushScope()
            declareVar(paramName, TypeName.ANY)
            line("for _, $paramName in ipairs($receiverStr) do")
            pushIndent()
            line("$paramName = __nilDecode($paramName)")
            val stmts = selector.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$resultVar = $resultVar + ($it)" } else emitStatement(stmt)
            }
            popIndent()
            line("end")
            popScope()
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        }
        else -> {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(selector)}")
            line("for _, __v in ipairs($receiverStr) do $resultVar = $resultVar + $fnVar(__nilDecode(__v)) end")
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        }
    }
}
