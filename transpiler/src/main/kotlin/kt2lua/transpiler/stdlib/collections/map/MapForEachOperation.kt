package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("Map")
class MapForEachOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Map", "forEach", listOf("(K, V) -> Unit"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String? = null
    override fun needsResultVar() = false
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        ctx.emitMapForEach(ctx.hoistIfNeeded(call.receiver!!), call.args[0].toExpression())
        return true
    }
}
