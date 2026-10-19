package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitMapIndexedStatements(resultVar: String, receiverStr: String, callback: LambdaExpr) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    val idxParam = callback.params.getOrElse(0) { "index" }
    val elemParam = callback.params.getOrElse(1) { "it" }
    pushScope()
    declareVar(idxParam, TypeName.INT)
    declareVar(elemParam, TypeName.ANY)
    line("for __i, $elemParam in ipairs($receiverStr) do")
    pushIndent()
    line("local $idxParam = __i - 1")
    line("$elemParam = __nilDecode($elemParam)")
    val stmts = callback.body.body
    stmts.forEachIndexed { i, stmt ->
        if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$resultVar[__i] = __nilEncode($it)" } else emitStatement(stmt)
    }
    popIndent()
    line("end")
    popScope()
}

fun CollectionLoweringContext.emitForEachIndexedStatement(receiverStr: String, callback: LambdaExpr) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    val idxParam = callback.params.getOrElse(0) { "index" }
    val elemParam = callback.params.getOrElse(1) { "it" }
    pushScope()
    declareVar(idxParam, TypeName.INT)
    declareVar(elemParam, TypeName.ANY)
    line("for __i, $elemParam in ipairs($receiverStr) do")
    pushIndent()
    line("local $idxParam = __i - 1")
    line("$elemParam = __nilDecode($elemParam)")
    callback.body.body.forEach { emitStatement(it) }
    popIndent()
    line("end")
    popScope()
}

fun CollectionLoweringContext.emitFilterIndexedStatements(resultVar: String, receiverStr: String, callback: LambdaExpr) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    val idxParam = callback.params.getOrElse(0) { "index" }
    val elemParam = callback.params.getOrElse(1) { "it" }
    pushScope()
    declareVar(idxParam, TypeName.INT)
    declareVar(elemParam, TypeName.ANY)
    line("for __i, $elemParam in ipairs($receiverStr) do")
    pushIndent()
    line("local $idxParam = __i - 1")
    line("local __raw = $elemParam")
    line("$elemParam = __nilDecode($elemParam)")
    val stmts = callback.body.body
    stmts.forEachIndexed { i, stmt ->
        if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then table.insert($resultVar, __raw) end" } else emitStatement(stmt)
    }
    popIndent()
    line("end")
    popScope()
}

fun CollectionLoweringContext.emitMapNotNullStatements(resultVar: String, receiverStr: String, callback: Expression) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("$paramName = __nilDecode($paramName)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "local __r = $it; if __r ~= nil then table.insert($resultVar, __r) end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("local __r = $fnVar(__nilDecode(__v)); if __r ~= nil then table.insert($resultVar, __r) end")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitFilterNotNullStatements(resultVar: String, receiverStr: String) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    line("for _, __v in ipairs($receiverStr) do if __v ~= __NULL then table.insert($resultVar, __v) end end")
}
