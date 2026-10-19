package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.aggregateReceiverStr(call: OperationCall): String {
    val receiver = call.receiver!!
    return hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }
}

@Receivers("List", "Set")
class AnyAllNoneOperation(private val mode: String) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", mode, listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "$mode always produces a result" }
        ctx.emitAnyAllNoneStatements(target, ctx.aggregateReceiverStr(call), call.args[0].toExpression(), mode)
        return true
    }

    companion object {
        val ALL = listOf("any", "all", "none").map { AnyAllNoneOperation(it) }
    }
}

@Receivers("List", "Set")
class CountOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Collection", "count", listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size <= 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "count always produces a result" }
        ctx.emitCountStatements(target, ctx.aggregateReceiverStr(call), call.args.getOrNull(0)?.toExpression())
        return true
    }
}

@Receivers("List", "Set")
class SumOperation(methodName: String, private val hasSelector: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName, if (hasSelector) listOf("(T) -> R") else emptyList())
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) =
        call.args.size == (if (hasSelector) 1 else 0) && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "sum always produces a result" }
        val selector = if (hasSelector) call.args[0].toExpression() else null
        ctx.emitSumStatements(target, ctx.aggregateReceiverStr(call), selector)
        return true
    }

    companion object {
        val ALL = listOf(SumOperation("sum", hasSelector = false), SumOperation("sumOf", hasSelector = true))
    }
}
