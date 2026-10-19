package kt2lua.transpiler.stdlib.registry

import kt2lua.transpiler.CallArg
import kt2lua.transpiler.Expression
import kt2lua.transpiler.compatibility.DeclarationId

class OperationCall(
    val receiver: Expression?,
    val args: List<CallArg>,
    val elementClass: String? = null,
)

interface StdlibOperation {
    val id: DeclarationId

    fun matches(call: OperationCall, ctx: CollectionLoweringContext): Boolean

    fun resultKind(receiverKind: String?): String?

    fun needsResultVar(): Boolean = true

    fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean
}
