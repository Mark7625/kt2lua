package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

private fun CollectionLoweringContext.finalReceiverStr(call: OperationCall): String {
    val receiver = call.receiver!!
    return hoistIfNeeded(receiver).let { if (receiverInstanceClass(receiver) == "Set") "$it.list" else it }
}

@Receivers("List", "Set")
class PartitionOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "partition", listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "partition always produces a result" }
        ctx.emitPartitionStatements(target, ctx.finalReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class FlattenOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "flatten")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "flatten always produces a result" }
        ctx.emitFlattenStatements(target, ctx.finalReceiverStr(call))
        return true
    }
}

@Receivers("List", "Set")
class ReversedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "reversed")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "reversed always produces a result" }
        ctx.emitReversedStatements(target, ctx.finalReceiverStr(call))
        return true
    }
}

@Receivers("List", "Set")
class ZipOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "zip", listOf("Iterable<R>"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "zip always produces a result" }
        val otherArr = ctx.iterationArrayOf(call.args[0].toExpression())
        ctx.emitZipStatements(target, ctx.finalReceiverStr(call), otherArr)
        return true
    }
}

@Receivers("List", "Set")
class ZipWithNextOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "zipWithNext")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "zipWithNext always produces a result" }
        ctx.emitZipWithNextStatements(target, ctx.finalReceiverStr(call))
        return true
    }
}

@Receivers("List", "Set")
class WindowedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "windowed", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "windowed always produces a result" }
        ctx.emitWindowedStatements(target, ctx.finalReceiverStr(call), ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class SortedWithOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "sortedWith", listOf("(T, T) -> Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "sortedWith always produces a result" }
        ctx.emitSortedWithStatements(target, ctx.finalReceiverStr(call), call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class ToListOperation(methodName: String) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName)
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "${id.name} always produces a result" }
        ctx.emitToListStatements(target, ctx.finalReceiverStr(call))
        return true
    }

    companion object {
        val ALL = listOf(ToListOperation("toList"), ToListOperation("toMutableList"))
    }
}

@Receivers("List", "Set")
class ToSetOperation(methodName: String) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName)
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "Set"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "${id.name} always produces a result" }
        ctx.emitToSetStatements(target, ctx.finalReceiverStr(call))
        return true
    }

    companion object {
        val ALL = listOf(ToSetOperation("toSet"), ToSetOperation("toMutableSet"))
    }
}

@Receivers("List", "Set")
class ToMapOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "toMap")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty() && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "toMap always produces a result" }
        ctx.emitToMapStatements(target, ctx.finalReceiverStr(call))
        return true
    }
}
