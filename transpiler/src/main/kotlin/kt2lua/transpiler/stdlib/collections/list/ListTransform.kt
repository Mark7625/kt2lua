package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitMapStatements(resultVar: String, receiverStr: String, callback: Expression, elementClass: String?) {
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        elementClass?.let { declareInstance(paramName, it) }
        line("for __i, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$resultVar[__i] = $it" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i, __v in ipairs($receiverStr) do")
        pushIndent()
        line("$resultVar[__i] = $fnVar(__v)")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitFilterStatements(resultVar: String, receiverStr: String, callback: Expression, elementClass: String?) {
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        elementClass?.let { declareInstance(paramName, it) }
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then table.insert($resultVar, $paramName) end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if $fnVar(__v) then table.insert($resultVar, __v) end")
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
        line("$resultVar = $fnVar($resultVar, __v)")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitReduceStatements(resultVar: String, receiverStr: String, callback: Expression) {
    if (callback is LambdaExpr) {
        val accParam = callback.params.getOrElse(0) { "acc" }
        val xParam = callback.params.getOrElse(1) { "it" }
        line("local $accParam = $receiverStr[1]")
        pushScope()
        declareVar(accParam, TypeName.ANY)
        declareVar(xParam, TypeName.ANY)
        line("for __i = 2, #$receiverStr do")
        pushIndent()
        line("local $xParam = $receiverStr[__i]")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$accParam = $it" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
        if (resultVar != accParam) line("local $resultVar = $accParam")
    } else {
        line("local $resultVar = $receiverStr[1]")
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for __i = 2, #$receiverStr do")
        pushIndent()
        line("$resultVar = $fnVar($resultVar, $receiverStr[__i])")
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
        line("$fnVar($itemVar)")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitFlatMapStatements(resultVar: String, receiverStr: String, callback: Expression) {
    line("local $resultVar = {}")
    if (callback is LambdaExpr) {
        val paramName = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "for _, __e in ipairs($it) do table.insert($resultVar, __e) end" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("for _, __e in ipairs($fnVar(__v)) do table.insert($resultVar, __e) end")
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
    val seenVar = nextTemp("__seen")
    line("local $seenVar = {}")
    line("local $resultVar = {}")
    when {
        selector == null -> {
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
            val stmts = selector.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) {
                    emitLambdaResult(stmt) { "local __k = $it; if not $seenVar[__k] then $seenVar[__k] = true; table.insert($resultVar, $paramName) end" }
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
            line("local __k = $fnVar(__v); if not $seenVar[__k] then $seenVar[__k] = true; table.insert($resultVar, __v) end")
            popIndent()
            line("end")
        }
    }
}
