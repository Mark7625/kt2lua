package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class FoldOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "fold", listOf("R", "(R, T) -> R"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 2
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "fold always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitFoldStatements(target, receiverStr, call.args[0].toExpression(), call.args[1].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class ReduceOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "reduce", listOf("(T, T) -> T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "reduce always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitReduceStatements(target, receiverStr, call.args[0].toExpression())
        return true
    }
}
