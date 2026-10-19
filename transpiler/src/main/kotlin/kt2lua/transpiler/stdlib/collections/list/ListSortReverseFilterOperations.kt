package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.LambdaExpr
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List")
class ListSortOperation(methodName: String, private val hasSelector: Boolean, private val descending: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableList", methodName, if (hasSelector) listOf("(T) -> R") else emptyList())
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == (if (hasSelector) 1 else 0)
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiverStr = ctx.hoistIfNeeded(call.receiver!!)
        val selector = if (hasSelector) call.args[0].toExpression() else null
        val sortedVar = ctx.nextTemp("__sorted")
        ctx.emitSortedStatements(sortedVar, receiverStr, selector, descending)
        ctx.line("for __i, __v in ipairs($sortedVar) do $receiverStr[__i] = __v end")
        return true
    }

    companion object {
        val ALL = listOf(
            ListSortOperation("sort", hasSelector = false, descending = false),
            ListSortOperation("sortBy", hasSelector = true, descending = false),
            ListSortOperation("sortByDescending", hasSelector = true, descending = true),
        )
    }
}

@Receivers("List")
class ListReverseOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableList", "reverse")
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.isEmpty()
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        ctx.emitInPlaceReverseStatements(ctx.hoistIfNeeded(call.receiver!!))
        return true
    }
}

@Receivers("List")
class ListFilterLambdaOperation(methodName: String, private val keep: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableIterable", methodName, listOf("(T) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && call.args[0].toExpression() is LambdaExpr
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        ctx.emitInPlaceFilterStatements(ctx.hoistIfNeeded(call.receiver!!), call.args[0].toExpression() as LambdaExpr, keep)
        return true
    }

    companion object {
        val ALL = listOf(ListFilterLambdaOperation("removeAll", keep = false), ListFilterLambdaOperation("retainAll", keep = true))
    }
}

@Receivers("List")
class ListFilterMembershipOperation(methodName: String, private val keep: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "MutableCollection", methodName, listOf("Collection"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1 && call.args[0].toExpression() !is LambdaExpr
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        ctx.emitInPlaceMembershipFilterStatements(ctx.hoistIfNeeded(call.receiver!!), call.args[0].toExpression(), keep)
        return true
    }

    companion object {
        val ALL = listOf(ListFilterMembershipOperation("removeAll", keep = false), ListFilterMembershipOperation("retainAll", keep = true))
    }
}
