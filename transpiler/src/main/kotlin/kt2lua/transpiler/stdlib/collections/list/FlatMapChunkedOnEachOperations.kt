package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class FlatMapOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "flatMap", listOf("(T) -> Iterable<R>"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "flatMap always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitFlatMapStatements(target, receiverStr, call.args[0].toExpression())
        return true
    }
}

@Receivers("List", "Set")
class ChunkedOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "chunked", listOf("Int"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "chunked always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        ctx.emitChunkedStatements(target, receiverStr, ctx.emitExpression(call.args[0].toExpression()))
        return true
    }
}

@Receivers("List", "Set")
class OnEachOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "onEach", listOf("(T) -> Unit"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = receiverKind
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "onEach always produces a result" }
        val receiver = call.receiver!!
        val hoisted = ctx.hoistIfNeeded(receiver)
        val receiverStr = if (ctx.receiverInstanceClass(receiver) == "Set") "$hoisted.list" else hoisted
        ctx.emitForEachStatement(receiverStr, call.args[0].toExpression(), call.elementClass)
        ctx.line("local $target = $hoisted")
        return true
    }
}
