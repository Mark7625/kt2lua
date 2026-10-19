package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class SortedOperation(methodName: String, private val hasSelector: Boolean, private val descending: Boolean) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", methodName, if (hasSelector) listOf("(T) -> R") else emptyList())
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) =
        call.args.size == (if (hasSelector) 1 else 0) && !ctx.isModuleCall(call.receiver!!)
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "${if (descending) "descending " else ""}sorted always produces a result" }
        val receiver = call.receiver!!
        val receiverStr = ctx.hoistIfNeeded(receiver).let { if (ctx.receiverInstanceClass(receiver) == "Set") "$it.list" else it }
        val selector = if (hasSelector) call.args[0].toExpression() else null
        ctx.emitSortedStatements(target, receiverStr, selector, descending)
        return true
    }

    companion object {
        val ALL = listOf(
            SortedOperation("sorted", hasSelector = false, descending = false),
            SortedOperation("sortedDescending", hasSelector = false, descending = true),
            SortedOperation("sortedBy", hasSelector = true, descending = false),
            SortedOperation("sortedByDescending", hasSelector = true, descending = true),
        )
    }
}
