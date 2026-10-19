package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitSortedStatements(resultVar: String, receiverStr: String, selector: Expression?, descending: Boolean) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    val keyedVar = nextTemp("__keyed")
    line("local $keyedVar = {}")
    when {
        selector == null -> line("for __i, __v in ipairs($receiverStr) do $keyedVar[__i] = {v = __v, k = __v, i = __i} end")
        selector is LambdaExpr -> {
            val paramName = selector.params.firstOrNull() ?: "it"
            pushScope()
            declareVar(paramName, TypeName.ANY)
            line("for __i, $paramName in ipairs($receiverStr) do")
            pushIndent()
            line("local __raw = $paramName")
            line("$paramName = __nilDecode($paramName)")
            val stmts = selector.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$keyedVar[__i] = {v = __raw, k = $it, i = __i}" } else emitStatement(stmt)
            }
            popIndent()
            line("end")
            popScope()
        }
        else -> {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(selector)}")
            line("for __i, __v in ipairs($receiverStr) do $keyedVar[__i] = {v = __v, k = $fnVar(__nilDecode(__v)), i = __i} end")
        }
    }
    val cmp = if (descending) "a.k > b.k" else "a.k < b.k"
    line("table.sort($keyedVar, function(a, b) if a.k == b.k then return a.i < b.i end return $cmp end)")
    line("local $resultVar = {}")
    line("for __i, __e in ipairs($keyedVar) do $resultVar[__i] = __e.v end")
}
