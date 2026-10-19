package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class DistinctOperation(methodName: String, private val hasSelector: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName, if (hasSelector) listOf("(T) -> K") else emptyList())
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) =
        call.args.size == (if (hasSelector) 1 else 0) && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "distinct always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        val selector = if (hasSelector) call.args[0].toExpression() else null
        ctx.emitDistinctStatements(target, receiverStr, selector)
        return true
    }

    companion object {
        val ALL = listOf(DistinctOperation("distinct", hasSelector = false), DistinctOperation("distinctBy", hasSelector = true))
    }
}
