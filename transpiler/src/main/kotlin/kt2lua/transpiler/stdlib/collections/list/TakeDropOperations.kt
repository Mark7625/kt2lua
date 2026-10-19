package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.Expression
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.sliceReceiverStr(receiver: Expression): String =
    hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }

@Receivers("List", "Set")
class TakeOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "take", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "take always produces a result" }
        ctx.emitTakeStatements(target, ctx.sliceReceiverStr(call.receiver!!), ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class DropOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "drop", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "drop always produces a result" }
        ctx.emitDropStatements(target, ctx.sliceReceiverStr(call.receiver!!), ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class TakeLastOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "List", "takeLast", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "takeLast always produces a result" }
        ctx.emitTakeLastStatements(target, ctx.sliceReceiverStr(call.receiver!!), ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class DropLastOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "List", "dropLast", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "dropLast always produces a result" }
        ctx.emitDropLastStatements(target, ctx.sliceReceiverStr(call.receiver!!), ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class TakeWhileOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "takeWhile", listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "takeWhile always produces a result" }
        ctx.emitTakeWhileStatements(target, ctx.sliceReceiverStr(call.receiver!!), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class DropWhileOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "dropWhile", listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "dropWhile always produces a result" }
        ctx.emitDropWhileStatements(target, ctx.sliceReceiverStr(call.receiver!!), call.args[0].toExpression())
        return true
    }
}
