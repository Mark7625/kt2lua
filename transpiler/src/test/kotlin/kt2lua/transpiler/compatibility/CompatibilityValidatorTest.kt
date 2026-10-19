package kt2lua.transpiler.compatibility

import kt2lua.transpiler.Expression
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.Statement
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.OperationRegistry
import kt2lua.transpiler.stdlib.registry.StdlibOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object UnusedContext : CollectionLoweringContext {
    override fun line(text: String) = error("not used")
    override fun pushIndent() = error("not used")
    override fun popIndent() = error("not used")
    override fun emitExpression(expr: Expression) = error("not used")
    override fun emitStatement(stmt: Statement) = error("not used")
    override fun pushScope() = error("not used")
    override fun popScope() = error("not used")
    override fun declareVar(name: String, type: TypeName) = error("not used")
    override fun declareInstance(name: String, className: String) = error("not used")
    override fun receiverInstanceClass(expr: Expression): String? = error("not used")
    override fun isNativeClass(className: String) = error("not used")
    override fun hoistIfNeeded(expr: Expression) = error("not used")
    override fun isRepeatableExpr(expr: Expression) = error("not used")
    override fun iterationArrayOf(expr: Expression) = error("not used")
    override fun emitLambdaResult(statement: Statement, sink: (String) -> String) = error("not used")
    override fun nextTemp(prefix: String) = error("not used")
    override fun listElementClassOf(name: String) = error("not used")
    override fun isModuleCall(expr: Expression) = error("not used")
    override fun useRuntimeHelper(helper: RuntimeHelper) = error("not used")
}

private class StubOperation(override val id: DeclarationId) : StdlibOperation {
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = true
    override fun resultKind(receiverKind: String?) = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext) = true
}

class CompatibilityValidatorTest {
    private val mapId = DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R"))
    private val filterId = DeclarationId("kotlin.collections", "Iterable", "filter", listOf("(T) -> Boolean"))
    private val runningFoldId = DeclarationId("kotlin.collections", "Iterable", "runningFold", listOf("R", "(R, T) -> R"))

    private fun manifestWith(vararg entries: DeclarationEntry) =
        CompatibilityManifest("kotlin-stdlib", "2.3.0", "kotlin.collections", null, entries.toList())

    @Test
    fun `no issues when every supported declaration has a matching registration and vice versa`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            DeclarationEntry(mapId, "map", "extension", CompatibilityStatus.SUPPORTED),
        )))
        val operations = OperationRegistry().apply { register("List", "map", StubOperation(mapId)) }
        assertTrue(CompatibilityValidator.validate(registry, operations).isEmpty())
    }

    @Test
    fun `flags a declaration marked supported with no registered implementation`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            DeclarationEntry(mapId, "map", "extension", CompatibilityStatus.SUPPORTED),
        )))
        val issues = CompatibilityValidator.validate(registry, OperationRegistry())
        assertEquals(1, issues.size)
        assertEquals(mapId, issues.single().id)
    }

    @Test
    fun `flags a declaration marked partial with no registered implementation too`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            DeclarationEntry(filterId, "filter", "extension", CompatibilityStatus.PARTIAL, limitations = listOf("x")),
        )))
        val issues = CompatibilityValidator.validate(registry, OperationRegistry())
        assertEquals(1, issues.size)
    }

    @Test
    fun `does not flag unsupported or unknown declarations for missing an implementation`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            DeclarationEntry(runningFoldId, "runningFold", "extension", CompatibilityStatus.UNSUPPORTED),
        )))
        assertTrue(CompatibilityValidator.validate(registry, OperationRegistry()).isEmpty())
    }

    @Test
    fun `flags an implementation registered for a declaration absent from the manifest`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith()))
        val operations = OperationRegistry().apply { register("List", "map", StubOperation(mapId)) }
        val issues = CompatibilityValidator.validate(registry, operations)
        assertEquals(1, issues.size)
        assertEquals(mapId, issues.single().id)
    }

    @Test
    fun `onlyIds restricts the 'supported but unregistered' direction, not the reverse`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            DeclarationEntry(mapId, "map", "extension", CompatibilityStatus.SUPPORTED),
            DeclarationEntry(filterId, "filter", "extension", CompatibilityStatus.SUPPORTED),
        )))
        val operations = OperationRegistry().apply { register("List", "runningFold", StubOperation(runningFoldId)) }
        val issues = CompatibilityValidator.validate(registry, operations, onlyIds = setOf(mapId))
        assertEquals(setOf(mapId, runningFoldId), issues.map { it.id }.toSet())
    }
}
