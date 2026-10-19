package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.collections.map.emitMapPut
import kt2lua.transpiler.stdlib.collections.set.emitSetAdd
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitPartitionStatements(resultVar: String, receiverStr: String, callback: Expression) {
    val matchVar = nextTemp("__match")
    val restVar = nextTemp("__rest")
    line("local $matchVar, $restVar = {}, {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) { "if $it then table.insert($matchVar, $paramName) else table.insert($restVar, $paramName) end" }
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
        line("if $fnVar(__v) then table.insert($matchVar, __v) else table.insert($restVar, __v) end")
        popIndent()
        line("end")
    }
    line("local $resultVar = {first = $matchVar, second = $restVar}")
}

fun CollectionLoweringContext.emitFlattenStatements(resultVar: String, receiverStr: String) {
    line("local $resultVar = {}")
    line("for _, __inner in ipairs($receiverStr) do")
    pushIndent()
    line("for _, __v in ipairs(__inner) do table.insert($resultVar, __v) end")
    popIndent()
    line("end")
}

fun CollectionLoweringContext.emitReversedStatements(resultVar: String, receiverStr: String) {
    val nVar = nextTemp("__n")
    line("local $nVar = #$receiverStr")
    line("local $resultVar = {}")
    line("for __i = 1, $nVar do $resultVar[__i] = $receiverStr[$nVar - __i + 1] end")
}

fun CollectionLoweringContext.emitZipStatements(resultVar: String, receiverStr: String, otherArr: String) {
    line("local $resultVar = {}")
    line("for __i = 1, math.min(#$receiverStr, #$otherArr) do table.insert($resultVar, {first = $receiverStr[__i], second = $otherArr[__i]}) end")
}

fun CollectionLoweringContext.emitZipWithNextStatements(resultVar: String, receiverStr: String) {
    line("local $resultVar = {}")
    line("for __i = 1, #$receiverStr - 1 do table.insert($resultVar, {first = $receiverStr[__i], second = $receiverStr[__i + 1]}) end")
}

fun CollectionLoweringContext.emitWindowedStatements(resultVar: String, receiverStr: String, sizeStr: String) {
    line("local $resultVar = {}")
    line("for __i = 1, #$receiverStr - ($sizeStr) + 1 do")
    pushIndent()
    line("local __w = {}")
    line("for __j = __i, __i + ($sizeStr) - 1 do table.insert(__w, $receiverStr[__j]) end")
    line("table.insert($resultVar, __w)")
    popIndent()
    line("end")
}

fun CollectionLoweringContext.emitSortedWithStatements(resultVar: String, receiverStr: String, comparator: Expression) {
    val cmpVar = nextTemp("__cmp")
    line("local $cmpVar = ${emitExpression(comparator)}")
    val keyedVar = nextTemp("__keyed")
    line("local $keyedVar = {}")
    line("for __i, __v in ipairs($receiverStr) do $keyedVar[__i] = {v = __v, i = __i} end")
    line("table.sort($keyedVar, function(a, b) local __c = $cmpVar(a.v, b.v); if __c == 0 then return a.i < b.i end return __c < 0 end)")
    line("local $resultVar = {}")
    line("for __i, __e in ipairs($keyedVar) do $resultVar[__i] = __e.v end")
}

fun CollectionLoweringContext.emitToListStatements(resultVar: String, receiverStr: String) {
    line("local $resultVar = {}")
    line("for _, __v in ipairs($receiverStr) do table.insert($resultVar, __v) end")
}

fun CollectionLoweringContext.emitToSetStatements(resultVar: String, receiverStr: String) {
    line("local $resultVar = {list = {}, has = {}}")
    line("for _, __e in ipairs($receiverStr) do")
    pushIndent()
    emitSetAdd(resultVar, "__e")
    popIndent()
    line("end")
}

fun CollectionLoweringContext.emitToMapStatements(resultVar: String, receiverStr: String) {
    line("local $resultVar = {keys = {}, map = {}}")
    line("for _, __p in ipairs($receiverStr) do")
    pushIndent()
    emitMapPut(resultVar, "__p.first", "__p.second")
    popIndent()
    line("end")
}
