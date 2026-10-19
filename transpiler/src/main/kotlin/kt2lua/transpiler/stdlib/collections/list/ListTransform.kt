package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

// Lists store Kotlin nulls as the __NULL sentinel (see RuntimeHelpers.NULL_SENTINEL) so ipairs()
// doesn't stop early on a real nil hole. Every value read out of a list for use in logic
// (predicates, transforms, printing) must be __nilDecode()'d; every value stored into a new
// list's backing table must be __nilEncode()'d first - including copy-through values, since
// decoding for predicate correctness strips the encoding the result list still needs.

fun CollectionLoweringContext.emitMapStatements(resultVar: String, receiverStr: String, callback: Expression, elementClass: String?) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        elementClass?.let { declareInstance(paramName, it) }
        line("for __i, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("$paramName = __nilDecode($paramName)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$resultVar[__i] = __nilEncode($it)" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i, __v in ipairs($receiverStr) do")
        pushIndent()
        line("$resultVar[__i] = __nilEncode($fnVar(__nilDecode(__v)))")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitFilterStatements(resultVar: String, receiverStr: String, callback: Expression, elementClass: String?) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        elementClass?.let { declareInstance(paramName, it) }
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("local __raw = $paramName")
        line("$paramName = __nilDecode($paramName)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then table.insert($resultVar, __raw) end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if $fnVar(__nilDecode(__v)) then table.insert($resultVar, __v) end")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitFoldStatements(resultVar: String, receiverStr: String, seed: Expression, callback: Expression) {
    if (callback is LambdaExpr) {
        val accParam = callback.params.getOrElse(0) { "acc" }
        val xParam = callback.params.getOrElse(1) { "it" }
        line("local $accParam = ${emitExpression(seed)}")
        pushScope()
        declareVar(accParam, TypeName.ANY)
        declareVar(xParam, TypeName.ANY)
        line("for _, $xParam in ipairs($receiverStr) do")
        pushIndent()
        line("$xParam = __nilDecode($xParam)")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$accParam = $it" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
        if (resultVar != accParam) line("local $resultVar = $accParam")
    } else {
        line("local $resultVar = ${emitExpression(seed)}")
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("$resultVar = $fnVar($resultVar, __nilDecode(__v))")
        popIndent()
        line("end")
    }
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
}

fun CollectionLoweringContext.emitReduceStatements(resultVar: String, receiverStr: String, callback: Expression) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    if (callback is LambdaExpr) {
        val accParam = callback.params.getOrElse(0) { "acc" }
        val xParam = callback.params.getOrElse(1) { "it" }
        line("local $accParam = __nilDecode($receiverStr[1])")
        pushScope()
        declareVar(accParam, TypeName.ANY)
        declareVar(xParam, TypeName.ANY)
        line("for __i = 2, #$receiverStr do")
        pushIndent()
        line("local $xParam = __nilDecode($receiverStr[__i])")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$accParam = $it" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
        if (resultVar != accParam) line("local $resultVar = $accParam")
    } else {
        line("local $resultVar = __nilDecode($receiverStr[1])")
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i = 2, #$receiverStr do")
        pushIndent()
        line("$resultVar = $fnVar($resultVar, __nilDecode($receiverStr[__i]))")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitForEachStatement(receiverStr: String, callback: Expression, elementClass: String?) {
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        elementClass?.let { declareInstance(paramName, it) }
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        line("$paramName = __nilDecode($paramName)")
        callback.body.body.forEach { emitStatement(it) }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        val itemVar = nextTemp("__v")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, $itemVar in ipairs($receiverStr) do")
        pushIndent()
        line("$fnVar(__nilDecode($itemVar))")
        popIndent()
        line("end")
    }
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
}

fun CollectionLoweringContext.emitFlatMapStatements(resultVar: String, receiverStr: String, callback: Expression) {
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
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "for _, __e in ipairs($it) do table.insert($resultVar, __nilEncode(__e)) end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("for _, __e in ipairs($fnVar(__nilDecode(__v))) do table.insert($resultVar, __nilEncode(__e)) end")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitChunkedStatements(resultVar: String, receiverStr: String, sizeStr: String) {
    line("local $resultVar = {}")
    line("local __chunk")
    line("for __i, __v in ipairs($receiverStr) do")
    pushIndent()
    line("if (__i - 1) % ($sizeStr) == 0 then __chunk = {}; table.insert($resultVar, __chunk) end")
    line("table.insert(__chunk, __v)")
    popIndent()
    line("end")
}

fun CollectionLoweringContext.emitDistinctStatements(resultVar: String, receiverStr: String, selector: Expression?) {
    useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
    val seenVar = nextTemp("__seen")
    line("local $seenVar = {}")
    line("local $resultVar = {}")
    when {
        selector == null -> {
            // __v (still encoded) is used directly as the dedup key - a decoded real nil can't
            // be a Lua table key ($seenVar[nil] errors), but the sentinel can.
            line("for _, __v in ipairs($receiverStr) do")
            pushIndent()
            line("if not $seenVar[__v] then $seenVar[__v] = true; table.insert($resultVar, __v) end")
            popIndent()
            line("end")
        }
        selector is LambdaExpr -> {
            val paramName = selector.params.firstOrNull() ?: "it"
            pushScope()
            declareVar(paramName, TypeName.ANY)
            line("for _, $paramName in ipairs($receiverStr) do")
            pushIndent()
            line("local __raw = $paramName")
            line("$paramName = __nilDecode($paramName)")
            val stmts = selector.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) {
                    emitLambdaResult(stmt) { "local __k = $it; if not $seenVar[__k] then $seenVar[__k] = true; table.insert($resultVar, __raw) end" }
                } else emitStatement(stmt)
            }
            popIndent()
            line("end")
            popScope()
        }
        else -> {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(selector)}")
            line("for _, __v in ipairs($receiverStr) do")
            pushIndent()
            line("local __k = $fnVar(__nilDecode(__v)); if not $seenVar[__k] then $seenVar[__k] = true; table.insert($resultVar, __v) end")
            popIndent()
            line("end")
        }
    }
}
