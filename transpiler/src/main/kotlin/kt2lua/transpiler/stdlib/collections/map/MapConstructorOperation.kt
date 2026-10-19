package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.SpreadArg
import kt2lua.transpiler.ToPairExpr
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression

class MapConstructorOperation(calleeName: String, parameters: List<String> = listOf("Pair<K, V>")) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", null, calleeName, parameters)

    override fun matches(call: OperationCall, ctx: CollectionLoweringContext): Boolean = true

    override fun resultKind(receiverKind: String?): String = "Map"

    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "Map construction always produces a result" }
        ctx.line("local $target = {keys = {}, map = {}}")
        for (arg in call.args) {
            val valueExpr = arg.toExpression()
            when {
                arg is SpreadArg -> {
                    val srcStr = ctx.emitExpression(valueExpr)
                    ctx.line("for _, __p in ipairs($srcStr) do")
                    ctx.pushIndent()
                    ctx.emitMapPut(target, "__p.first", "__p.second")
                    ctx.popIndent()
                    ctx.line("end")
                }
                valueExpr is ToPairExpr -> ctx.emitMapPut(target, ctx.emitExpression(valueExpr.first), ctx.emitExpression(valueExpr.second), valueExpr.first)
                else -> {
                    val tmp = ctx.nextTemp("__p")
                    ctx.line("local $tmp = ${ctx.emitExpression(valueExpr)}")
                    ctx.emitMapPut(target, "$tmp.first", "$tmp.second")
                }
            }
        }
        return true
    }
}
