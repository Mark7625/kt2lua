package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitGroupByStatements(resultVar: String, receiverStr: String, keySelector: Expression) {
    line("local $resultVar = {keys = {}, map = {}}")
    if (keySelector is LambdaExpr) {
        val paramName = keySelector.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = keySelector.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) {
                    "local __k = $it; if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k); $resultVar.map[__k] = {} end; table.insert($resultVar.map[__k], $paramName)"
                }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(keySelector)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("local __k = $fnVar(__v)")
        line("if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k); $resultVar.map[__k] = {} end")
        line("table.insert($resultVar.map[__k], __v)")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitAssociateStatements(resultVar: String, receiverStr: String, callback: Expression) {
    line("local $resultVar = {keys = {}, map = {}}")
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
                    "local __p = $it; if $resultVar.map[__p.first] == nil then table.insert($resultVar.keys, __p.first) end; $resultVar.map[__p.first] = __p.second"
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
        line("local __p = $fnVar(__v)")
        line("if $resultVar.map[__p.first] == nil then table.insert($resultVar.keys, __p.first) end")
        line("$resultVar.map[__p.first] = __p.second")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitAssociateByStatements(resultVar: String, receiverStr: String, keySelector: Expression) {
    line("local $resultVar = {keys = {}, map = {}}")
    if (keySelector is LambdaExpr) {
        val paramName = keySelector.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = keySelector.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) { "local __k = $it; if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k) end; $resultVar.map[__k] = $paramName" }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(keySelector)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("local __k = $fnVar(__v)")
        line("if $resultVar.map[__k] == nil then table.insert($resultVar.keys, __k) end")
        line("$resultVar.map[__k] = __v")
        popIndent()
        line("end")
    }
}

fun CollectionLoweringContext.emitAssociateWithStatements(resultVar: String, receiverStr: String, valueSelector: Expression) {
    line("local $resultVar = {keys = {}, map = {}}")
    if (valueSelector is LambdaExpr) {
        val paramName = valueSelector.params.firstOrNull() ?: "it"
        pushScope()
        declareVar(paramName, TypeName.ANY)
        line("for _, $paramName in ipairs($receiverStr) do")
        pushIndent()
        val stmts = valueSelector.body.body
        stmts.forEachIndexed { i, stmt ->
            if (i == stmts.lastIndex) {
                emitLambdaResult(stmt) { "if $resultVar.map[$paramName] == nil then table.insert($resultVar.keys, $paramName) end; $resultVar.map[$paramName] = $it" }
            } else emitStatement(stmt)
        }
        popIndent()
        line("end")
        popScope()
    } else {
        val fnVar = nextTemp("__fn")
        line("local $fnVar = ${emitExpression(valueSelector)}")
        line("for _, __v in ipairs($receiverStr) do")
        pushIndent()
        line("if $resultVar.map[__v] == nil then table.insert($resultVar.keys, __v) end")
        line("$resultVar.map[__v] = $fnVar(__v)")
        popIndent()
        line("end")
    }
}
