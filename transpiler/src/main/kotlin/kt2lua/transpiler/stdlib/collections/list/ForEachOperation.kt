package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("List", "Set")
class CollectionForEachOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Iterable", "forEach", listOf("(T) -> Unit"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val receiver = call.receiver!!
        ctx.emitForEachStatement(ctx.iterationArrayOf(receiver), call.args[0].toExpression(), call.elementClass)
        return true
    }
}
