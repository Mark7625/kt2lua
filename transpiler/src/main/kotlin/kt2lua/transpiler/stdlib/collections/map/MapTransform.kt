package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitMapMapStatements(resultVar: String, mapStr: String, callback: Expression) {
    line("local $resultVar = {}")
    if (callback is LambdaExpr && callback.params.size == 2) {
        val (keyParam, valueParam) = callback.params
        pushScope()
        declareVar(keyParam, TypeName.ANY)
        declareVar(valueParam, TypeName.ANY)
        line("for __i, $keyParam in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $valueParam = $mapStr.map[$keyParam]")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$resultVar[__i] = $it" } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else if (callback is LambdaExpr) {
        val entryParam = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(entryParam, TypeName.ANY)
        line("for __i, __k in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $entryParam = {key = __k, value = $mapStr.map[__k]}")
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
        line("for __i, __k in ipairs($mapStr.keys) do")
        pushIndent()
        line("$resultVar[__i] = $fnVar({key = __k, value = $mapStr.map[__k]})")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitMapFilterStatements(resultVar: String, mapStr: String, callback: Expression) {
    line("local $resultVar = {keys = {}, map = {}}")
    if (callback is LambdaExpr && callback.params.size == 2) {
        val (keyParam, valueParam) = callback.params
        pushScope()
        declareVar(keyParam, TypeName.ANY)
        declareVar(valueParam, TypeName.ANY)
        line("for _, $keyParam in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $valueParam = $mapStr.map[$keyParam]")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) {
                    "if $it then if $resultVar.map[$keyParam] == nil then table.insert($resultVar.keys, $keyParam) end; $resultVar.map[$keyParam] = $valueParam end"
                }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else if (callback is LambdaExpr) {
        val entryParam = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(entryParam, TypeName.ANY)
        line("for _, __k in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $entryParam = {key = __k, value = $mapStr.map[__k]}")
        val stmts = callback.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) {
                    "if $it then if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k) end; $resultVar.map[__k] = $entryParam.value end"
                }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, __k in ipairs($mapStr.keys) do")
        pushIndent()
        line("if $fnVar({key = __k, value = $mapStr.map[__k]}) then")
        pushIndent()
        line("if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k) end")
        line("$resultVar.map[__k] = $mapStr.map[__k]")
        popIndent()
        line("end")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitMapForEach(mapStr: String, callback: Expression) {
    if (callback is LambdaExpr && callback.params.size == 2) {
        val (keyParam, valueParam) = callback.params
        pushScope()
        declareVar(keyParam, TypeName.ANY)
        declareVar(valueParam, TypeName.ANY)
        line("for _, $keyParam in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $valueParam = $mapStr.map[$keyParam]")
        callback.body.body.forEach { emitStatement(it) }
        popIndent()
        line("end")
        popScope()
    } else if (callback is LambdaExpr) {
        val entryParam = callback.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(entryParam, TypeName.ANY)
        line("for _, __k in ipairs($mapStr.keys) do")
        pushIndent()
        line("local $entryParam = {key = __k, value = $mapStr.map[__k]}")
        callback.body.body.forEach { emitStatement(it) }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        val keyVar = nextTemp("__k")
        line("local $fnVar = ${emitExpression(callback)}")
        line("for _, $keyVar in ipairs($mapStr.keys) do")
        pushIndent()
        line("$fnVar({key = $keyVar, value = $mapStr.map[$keyVar]})")
        popIndent()
        line("end")
    }
}
