package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.searchReceiverStr(call: OperationCall): String {
    val receiver = call.receiver!!
    return hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }
}

@Receivers("List", "Set")
class FindOperation(methodName: String, private val fromEnd: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName, listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "${methodNameFor()} always produces a result" }
        ctx.emitFindStatements(target, ctx.searchReceiverStr(call), call.args[0].toExpression(), fromEnd)
        return true
    }
    private fun methodNameFor() = id.name

    companion object {
        val ALL = listOf(
            FindOperation("first", fromEnd = false), FindOperation("find", fromEnd = false), FindOperation("firstOrNull", fromEnd = false),
            FindOperation("last", fromEnd = true), FindOperation("lastOrNull", fromEnd = true),
        )
    }
}

@Receivers("List", "Set")
class IndexOfOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "indexOf", listOf("T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "indexOf always produces a result" }
        val targetExpr = call.args[0].toExpression()
        ctx.emitIndexOfStatements(target, ctx.searchReceiverStr(call), ctx.emitExpression(targetExpr), targetExpr)
        return true
    }
}

@Receivers("List", "Set")
class IndexOfPredicateOperation(methodName: String, private val fromEnd: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName, listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "${id.name} always produces a result" }
        ctx.emitIndexOfPredicateStatements(target, ctx.searchReceiverStr(call), call.args[0].toExpression(), fromEnd)
        return true
    }

    companion object {
        val ALL = listOf(
            IndexOfPredicateOperation("indexOfFirst", fromEnd = false),
            IndexOfPredicateOperation("indexOfLast", fromEnd = true),
        )
    }
}
