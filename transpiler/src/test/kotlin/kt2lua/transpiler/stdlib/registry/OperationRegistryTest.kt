package kt2lua.transpiler.stdlib.registry

import kt2lua.transpiler.Expression
import kt2lua.transpiler.Statement
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.compatibility.DeclarationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object UnusedContext : CollectionLoweringContext {
    override fun line(text: String) = error("not used in this test")
    override fun pushIndent() = error("not used in this test")
    override fun popIndent() = error("not used in this test")
    override fun emitExpression(expr: Expression) = error("not used in this test")
    override fun emitStatement(stmt: Statement) = error("not used in this test")
    override fun pushScope() = error("not used in this test")
    override fun popScope() = error("not used in this test")
    override fun declareVar(name: String, type: TypeName) = error("not used in this test")
    override fun declareInstance(name: String, className: String) = error("not used in this test")
    override fun receiverInstanceClass(expr: Expression): String? = error("not used in this test")
    override fun isNativeClass(className: String) = error("not used in this test")
    override fun hoistIfNeeded(expr: Expression) = error("not used in this test")
    override fun isRepeatableExpr(expr: Expression) = error("not used in this test")
    override fun iterationArrayOf(expr: Expression) = error("not used in this test")
    override fun emitLambdaResult(statement: Statement, sink: (String) -> String) = error("not used in this test")
    override fun nextTemp(prefix: String) = error("not used in this test")
    override fun listElementClassOf(name: String) = error("not used in this test")
    override fun isModuleCall(expr: Expression) = error("not used in this test")
}

private class FakeOperation(override val id: DeclarationId, private val argCount: Int) : StdlibOperation {
    var lowered = false
    override fun matches(call: OperationCall, ctx: CollectionLoweringContext) = call.args.size == argCount
    override fun resultKind(receiverKind: String?) = "List"
    override fun lower(call: OperationCall, resultVar: String?, ctx: CollectionLoweringContext): Boolean {
        lowered = true
        return true
    }
}

class OperationRegistryTest {
    @Test
    fun `resolves a registered member operation by receiver kind and method name`() {
        val registry = OperationRegistry()
        val op = FakeOperation(DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R")), argCount = 1)
        registry.register("List", "map", op)
        val resolved = registry.resolve("List", "map", OperationCall(null, emptyList()), UnusedContext)
        assertNull(resolved)
        val matched = registry.resolve("List", "map", OperationCall(null, listOf(fakeArg())), UnusedContext)
        assertEquals(op, matched)
    }

    @Test
    fun `an unregistered receiver kind or method name resolves to nothing`() {
        val registry = OperationRegistry()
        registry.register("List", "map", FakeOperation(DeclarationId("kotlin.collections", "Iterable", "map"), argCount = 1))
        assertNull(registry.resolve("Set", "map", OperationCall(null, listOf(fakeArg())), UnusedContext))
        assertNull(registry.resolve("List", "filter", OperationCall(null, listOf(fakeArg())), UnusedContext))
    }

    @Test
    fun `multiple overloads under the same name are tried in registration order until one matches`() {
        val registry = OperationRegistry()
        val oneArg = FakeOperation(DeclarationId("kotlin.collections", "Iterable", "fold", listOf("R")), argCount = 1)
        val twoArg = FakeOperation(DeclarationId("kotlin.collections", "Iterable", "fold", listOf("R", "(R, T) -> R")), argCount = 2)
        registry.register("List", "fold", oneArg)
        registry.register("List", "fold", twoArg)
        assertEquals(oneArg, registry.resolve("List", "fold", OperationCall(null, listOf(fakeArg())), UnusedContext))
        assertEquals(twoArg, registry.resolve("List", "fold", OperationCall(null, listOf(fakeArg(), fakeArg())), UnusedContext))
    }

    @Test
    fun `top-level calls are resolved separately from member calls`() {
        val registry = OperationRegistry()
        val listOfOp = FakeOperation(DeclarationId("kotlin.collections", null, "listOf"), argCount = 0)
        registry.registerTopLevel("listOf", listOfOp)
        assertEquals(listOfOp, registry.resolveTopLevel("listOf", OperationCall(null, emptyList()), UnusedContext))
        assertNull(registry.resolve("List", "listOf", OperationCall(null, emptyList()), UnusedContext))
    }

    @Test
    fun `registeredIds covers both member and top-level registrations`() {
        val registry = OperationRegistry()
        val memberOp = FakeOperation(DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R")), argCount = 1)
        val topLevelOp = FakeOperation(DeclarationId("kotlin.collections", null, "listOf"), argCount = 0)
        registry.register("List", "map", memberOp)
        registry.registerTopLevel("listOf", topLevelOp)
        assertTrue(registry.contains(memberOp.id))
        assertTrue(registry.contains(topLevelOp.id))
        assertEquals(setOf(memberOp.id, topLevelOp.id), registry.registeredIds())
    }

    private fun fakeArg() = kt2lua.transpiler.PositionalArg(kt2lua.transpiler.IntLiteral(0))
}
