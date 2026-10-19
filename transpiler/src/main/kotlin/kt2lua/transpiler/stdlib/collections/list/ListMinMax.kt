package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitMinMaxStatements(resultVar: String, receiverStr: String, selector: Expression?, findMax: Boolean) {
    val bestKeyVar = nextTemp("__bestKey")
    line("local $resultVar = nil")
    line("local $bestKeyVar = nil")
    val cmp = if (findMax) ">" else "<"
    when {
        selector == null -> {
            line("for _, __v in ipairs($receiverStr) do")
            pushIndent()
            line("if $bestKeyVar == nil or __v $cmp $bestKeyVar then $bestKeyVar = __v; $resultVar = __v end")
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
                    emitLambdaResult(stmt) { "local __k = $it; if $bestKeyVar == nil or __k $cmp $bestKeyVar then $bestKeyVar = __k; $resultVar = $paramName end" }
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
            line("local __k = $fnVar(__v); if $bestKeyVar == nil or __k $cmp $bestKeyVar then $bestKeyVar = __k; $resultVar = __v end")
            popIndent()
            line("end")
        }
    }
}
