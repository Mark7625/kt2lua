package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitInPlaceReverseStatements(receiverStr: String) {
    val nVar = nextTemp("__n")
    line("local $nVar = #$receiverStr")
    line("for __i = 1, math.floor($nVar / 2) do $receiverStr[__i], $receiverStr[$nVar - __i + 1] = $receiverStr[$nVar - __i + 1], $receiverStr[__i] end")
}

fun CollectionLoweringContext.emitInPlaceFilterStatements(receiverStr: String, callback: LambdaExpr, keep: Boolean) {
    val keepVar = nextTemp("__keep")
    line("local $keepVar = {}")
    val paramName = callback.params.firstOrNull() ?: "it"
    pushScope()
    declareVar(paramName, TypeName.ANY)
    line("for _, $paramName in ipairs($receiverStr) do")
    pushIndent()
    val stmts = callback.body.body
    stmts.forEachIndexed { i, stmt ->
        if (i == stmts.lastIndex) {
            emitLambdaResult(stmt) { v -> if (keep) "if $v then table.insert($keepVar, $paramName) end" else "if not ($v) then table.insert($keepVar, $paramName) end" }
        } else emitStatement(stmt)
    }
    popIndent()
    line("end")
    popScope()
    line("for __i = #$receiverStr, 1, -1 do table.remove($receiverStr, __i) end")
    line("for __i, __v in ipairs($keepVar) do $receiverStr[__i] = __v end")
}

fun CollectionLoweringContext.emitInPlaceMembershipFilterStatements(receiverStr: String, otherExpr: Expression, keep: Boolean) {
    val otherArr = iterationArrayOf(otherExpr)
    val setVar = nextTemp("__set")
    val keepVar = nextTemp("__keep")
    line("local $setVar = {}")
    line("for _, __v in ipairs($otherArr) do $setVar[__v] = true end")
    line("local $keepVar = {}")
    line("for _, __v in ipairs($receiverStr) do if ($setVar[__v] ~= nil) == $keep then table.insert($keepVar, __v) end end")
    line("for __i = #$receiverStr, 1, -1 do table.remove($receiverStr, __i) end")
    line("for __i, __v in ipairs($keepVar) do $receiverStr[__i] = __v end")
}
