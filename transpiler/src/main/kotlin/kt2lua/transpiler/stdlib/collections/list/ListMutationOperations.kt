package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.collections.common.structuralEq
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List")
class ListAddOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableList", "add", listOf("T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        ctx.line("table.insert($receiverStr, ${ctx.emitExpression(call.args[0].toExpression())})")
        return true
    }
}

@Receivers("List")
class ListAddAtOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableList", "add", listOf("Int", "T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 2
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val idx = ctx.emitExpression(call.args[0].toExpression())
        val value = ctx.emitExpression(call.args[1].toExpression())
        ctx.line("table.insert($receiverStr, ($idx) + 1, $value)")
        return true
    }
}

@Receivers("List")
class ListRemoveOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableCollection", "remove", listOf("T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val targetExpr = call.args[0].toExpression()
        val target = ctx.emitExpression(targetExpr)
        ctx.line("for __i, __v in ipairs($receiverStr) do if ${ctx.structuralEq(targetExpr, "__v", target)} then table.remove($receiverStr, __i); break end end")
        return true
    }
}

@Receivers("List")
class ListSetOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableList", "set", listOf("Int", "T"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 2
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val idx = ctx.emitExpression(call.args[0].toExpression())
        val value = ctx.emitExpression(call.args[1].toExpression())
        ctx.line("$receiverStr[($idx) + 1] = $value")
        return true
    }
}

@Receivers("List")
class ListClearOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableCollection", "clear")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty()
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        ctx.line("for __i = #$receiverStr, 1, -1 do table.remove($receiverStr, __i) end")
        return true
    }
}

@Receivers("List")
class ListAddAllOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableCollection", "addAll", listOf("Collection"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val srcArr = ctx.iterationArrayOf(call.args[0].toExpression())
        ctx.line("for _, __e in ipairs($srcArr) do table.insert($receiverStr, __e) end")
        return true
    }
}
