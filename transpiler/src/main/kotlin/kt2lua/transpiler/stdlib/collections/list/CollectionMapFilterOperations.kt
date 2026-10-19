package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class CollectionMapOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "map always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitMapStatements(target, receiverStr, call.args[0].toExpression(), call.elementClass)
        return true
    }
}

@Receivers("List", "Set")
class CollectionFilterOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "filter", listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "filter always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitFilterStatements(target, receiverStr, call.args[0].toExpression(), call.elementClass)
        return true
    }
}
