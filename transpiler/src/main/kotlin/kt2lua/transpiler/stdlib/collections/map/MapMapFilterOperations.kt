package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression
import kt2lua.transpiler.stdlib.registry.Receivers

@Receivers("Map")
class MapMapOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Map", "map", listOf("(Map.Entry<K, V>) -> R"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "map always produces a result" }
        ctx.emitMapMapStatements(target, ctx.hoistIfNeeded(call.receiver!!), call.args[0].toExpression())
        return true
    }
}

@Receivers("Map")
class MapFilterOperation : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", "Map", "filter", listOf("(Map.Entry<K, V>) -> Boolean"))
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == 1
    override fun resultKind(receiverKind: String?): String = "Map"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "filter always produces a result" }
        ctx.emitMapFilterStatements(target, ctx.hoistIfNeeded(call.receiver!!), call.args[0].toExpression())
        return true
    }
}
