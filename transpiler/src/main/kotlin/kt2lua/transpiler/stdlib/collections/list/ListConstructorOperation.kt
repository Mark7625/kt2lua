package kt2lua.transpiler.stdlib.collections.list

import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.SpreadArg
import kt2lua.transpiler.compatibility.DeclarationId
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kt2lua.transpiler.toExpression

class ListConstructorOperation(calleeName: String, parameters: List<String> = listOf("T")) : StdlibOperation {
    override val id = DeclarationId("kotlin.collections", null, calleeName, parameters)

    override fun matches(call: OperationCall, ctx: CollectionLoweringContext): Boolean = true

    override fun resultKind(receiverKind: String?): String = "List"

    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        val target = requireNotNull(resultVar) { "List construction always produces a result" }
        if (call.args.isNotEmpty()) ctx.useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        if (call.args.none { it is SpreadArg }) {
            ctx.line("local $target = {${call.args.joinToString(", ") { "__nilEncode(${ctx.emitExpression(it.toExpression())})" }}}")
            return true
        }
        val plain = call.args.takeWhile { it !is SpreadArg }
        ctx.line("local $target = {${plain.joinToString(", ") { "__nilEncode(${ctx.emitExpression(it.toExpression())})" }}}")
        val spread = call.args.drop(plain.size).filterIsInstance<SpreadArg>().firstOrNull() ?: return true
        val srcStr = ctx.iterationArrayOf(spread.expr)
        ctx.line("for _, __e in ipairs($srcStr) do table.insert($target, __e) end")
        return true
    }
}
