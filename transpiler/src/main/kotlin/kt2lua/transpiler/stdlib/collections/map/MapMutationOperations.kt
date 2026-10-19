package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("Map")
class MapPutOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableMap", "put", listOf("K", "V"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 2
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val keyExpr = call.args[0].toExpression()
        ctx.emitMapPut(receiverStr, ctx.emitExpression(keyExpr), ctx.emitExpression(call.args[1].toExpression()), keyExpr)
        return true
    }
}

@Receivers("Map")
class MapRemoveOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableMap", "remove", listOf("K"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val keyExpr = call.args[0].toExpression()
        ctx.emitMapRemove(receiverStr, ctx.emitExpression(keyExpr), keyExpr)
        return true
    }
}

@Receivers("Map")
class MapClearOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableMap", "clear")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty()
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        ctx.line("$receiverStr.keys = {}")
        ctx.line("$receiverStr.map = {}")
        return true
    }
}
