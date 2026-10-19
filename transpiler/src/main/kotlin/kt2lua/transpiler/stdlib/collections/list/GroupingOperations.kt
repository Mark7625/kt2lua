package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.groupingReceiverStr(call: OperationCall): String {
    val receiver = call.receiver!!
    return hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }
}

@Receivers("List", "Set")
class GroupByOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "groupBy", listOf("(T) -> K"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "groupBy always produces a result" }
        ctx.emitGroupByStatements(target, ctx.groupingReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class AssociateOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "associate", listOf("(T) -> Pair<K, V>"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "associate always produces a result" }
        ctx.emitAssociateStatements(target, ctx.groupingReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class AssociateByOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "associateBy", listOf("(T) -> K"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "associateBy always produces a result" }
        ctx.emitAssociateByStatements(target, ctx.groupingReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class AssociateWithOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "associateWith", listOf("(T) -> V"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "associateWith always produces a result" }
        ctx.emitAssociateWithStatements(target, ctx.groupingReceiverStr(call), call.args[0].toExpression())
        return true
    }
}
