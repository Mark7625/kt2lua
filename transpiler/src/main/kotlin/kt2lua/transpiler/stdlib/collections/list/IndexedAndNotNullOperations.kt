package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.indexedReceiverStr(call: OperationCall): String {
    val receiver = call.receiver!!
    return hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }
}

@Receivers("List", "Set")
class MapIndexedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "mapIndexed", listOf("(Int, T) -> R"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) =
        call.args.size == 1 && call.args[0].toExpression() is LambdaExpr && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "mapIndexed always produces a result" }
        ctx.emitMapIndexedStatements(target, ctx.indexedReceiverStr(call), call.args[0].toExpression() as LambdaExpr)
        return true
    }
}

@Receivers("List", "Set")
class FilterIndexedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "filterIndexed", listOf("(Int, T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) =
        call.args.size == 1 && call.args[0].toExpression() is LambdaExpr && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "filterIndexed always produces a result" }
        ctx.emitFilterIndexedStatements(target, ctx.indexedReceiverStr(call), call.args[0].toExpression() as LambdaExpr)
        return true
    }
}

@Receivers("List", "Set")
class ForEachIndexedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "forEachIndexed", listOf("(Int, T) -> Unit"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && call.args[0].toExpression() is LambdaExpr
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        ctx.emitForEachIndexedStatement(ctx.indexedReceiverStr(call), call.args[0].toExpression() as LambdaExpr)
        return true
    }
}

@Receivers("List", "Set")
class MapNotNullOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "mapNotNull", listOf("(T) -> R"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "mapNotNull always produces a result" }
        ctx.emitMapNotNullStatements(target, ctx.indexedReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class FilterNotNullOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "filterNotNull")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "filterNotNull always produces a result" }
        ctx.emitFilterNotNullStatements(target, ctx.indexedReceiverStr(call))
        return true
    }
}
