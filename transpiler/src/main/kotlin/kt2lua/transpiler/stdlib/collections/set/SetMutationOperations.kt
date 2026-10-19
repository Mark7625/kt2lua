package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("Set")
class SetAddOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableSet", "add", listOf("T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val elemExpr = call.args[0].toExpression()
        ctx.emitSetAdd(receiverStr, ctx.emitExpression(elemExpr), elemExpr)
        return true
    }
}

@Receivers("Set")
class SetRemoveOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableSet", "remove", listOf("T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val elemExpr = call.args[0].toExpression()
        ctx.emitSetRemove(receiverStr, ctx.emitExpression(elemExpr), elemExpr)
        return true
    }
}

@Receivers("Set")
class SetClearOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableCollection", "clear")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty()
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        ctx.line("$receiverStr.list = {}")
        ctx.line("$receiverStr.has = {}")
        return true
    }
}
