package kt2lua.transpiler.stdlib.registry

import kt2lua.transpiler.compatibility.DeclarationId

class OperationRegistry {
    private val byReceiverAndName = mutableMapOf<Pair<String, String>, MutableList<StdlibOperation>>()
    private val topLevel = mutableMapOf<String, MutableList<StdlibOperation>>()

    fun register(receiverKind: String, methodName: String, operation: StdlibOperation) {
        byReceiverAndName.getOrPut(receiverKind to methodName) { mutableListOf() }.add(operation)
    }

    fun register(operation: StdlibOperation) {
        val receivers = operation.javaClass.getAnnotation(Receivers::class.java)
            ?: error("${operation.javaClass.simpleName} must be annotated with @Receivers to use register(operation)")
        receivers.kinds.forEach { register(it, operation.id.name, operation) }
    }

    fun registerTopLevel(calleeName: String, operation: StdlibOperation) {
        topLevel.getOrPut(calleeName) { mutableListOf() }.add(operation)
    }

    fun resolve(receiverKind: String, methodName: String, call: OperationCall, ctx: CollectionLoweringContext): StdlibOperation? =
        byReceiverAndName[receiverKind to methodName].orEmpty().firstOrNull { it.matches(call, ctx) }

    fun resolveTopLevel(calleeName: String, call: OperationCall, ctx: CollectionLoweringContext): StdlibOperation? =
        topLevel[calleeName].orEmpty().firstOrNull { it.matches(call, ctx) }

    fun registeredIds(): Set<DeclarationId> =
        (byReceiverAndName.values.asSequence() + topLevel.values.asSequence())
            .flatMap { it.asSequence() }
            .map { it.id }
            .toSet()

    fun contains(id: DeclarationId): Boolean = id in registeredIds()

    fun memberOperations(): Set<StdlibOperation> = byReceiverAndName.values.flatten().toSet()

    fun methodNames(receiverKind: String, needsResultVar: Boolean): Set<String> =
        byReceiverAndName.entries
            .filter { (key, ops) -> key.first == receiverKind && ops.any { it.needsResultVar() == needsResultVar } }
            .map { it.key.second }
            .toSet()
}
