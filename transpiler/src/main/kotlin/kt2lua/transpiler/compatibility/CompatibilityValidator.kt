package kt2lua.transpiler.compatibility

import kt2lua.transpiler.stdlib.registry.OperationRegistry

data class ConsistencyIssue(val id: DeclarationId, val message: String)

object CompatibilityValidator {
    fun validate(registry: CompatibilityRegistry, operations: OperationRegistry, onlyIds: Set<DeclarationId>? = null): List<ConsistencyIssue> {
        val issues = mutableListOf<ConsistencyIssue>()
        for (entry in registry.all()) {
            if (onlyIds != null && entry.id !in onlyIds) continue
            val hasImpl = operations.contains(entry.id)
            if (!hasImpl && entry.status in setOf(CompatibilityStatus.SUPPORTED, CompatibilityStatus.PARTIAL)) {
                issues += ConsistencyIssue(entry.id, "Marked ${entry.status.name.lowercase()} but no implementation is registered for it")
            }
        }
        for (id in operations.registeredIds()) {
            if (registry.entry(id) == null) {
                issues += ConsistencyIssue(id, "Implementation registered but no compatibility manifest entry exists for it")
            }
        }
        return issues
    }
}
