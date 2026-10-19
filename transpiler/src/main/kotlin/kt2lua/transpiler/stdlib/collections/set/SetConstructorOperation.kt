package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.Identifier
import kt2lua.transpiler.SpreadArg
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression

class SetConstructorOperation(calleeName: String, parameters: List<String> = listOf("T")) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", null, calleeName, parameters)

    override fun matches(call: OperationCall, ctx: CollectionLoweringContext): Boolean = true

    override fun resultKind(receiverKind: String?): String = "Set"

    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "Set construction always produces a result" }
        ctx.line("local $target = {list = {}, has = {}}")
        for (arg in call.args) {
            if (arg is SpreadArg) {
                val srcArr = ctx.iterationArrayOf(arg.expr)
                val elementClass = (arg.expr as? Identifier)?.let { ctx.listElementClassOf(it.name) }
                ctx.pushScope()
                elementClass?.let { ctx.declareInstance("__e", it) }
                ctx.line("for _, __e in ipairs($srcArr) do")
                ctx.pushIndent()
                ctx.emitSetAdd(target, "__e", elementClass?.let { Identifier("__e") })
                ctx.popIndent()
                ctx.line("end")
                ctx.popScope()
            } else {
                val elemExpr = arg.toExpression()
                ctx.emitSetAdd(target, ctx.emitExpression(elemExpr), elemExpr)
            }
        }
        return true
    }
}
