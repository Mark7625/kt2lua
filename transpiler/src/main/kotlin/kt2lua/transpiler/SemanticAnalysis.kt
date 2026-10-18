package kt2lua.transpiler

data class FunctionSig(val params: List<TypeName>, val returnType: TypeName, val rawReturnType: String = "")

/** Scoped variable-type lookup — push/pop around each Lua block [Emitter] opens, so a type
 * doesn't leak past the scope it's declared in. */
class TypeEnv {
    private val scopes = mutableListOf(mutableMapOf<String, TypeName>())
    fun push() = scopes.add(mutableMapOf())
    fun pop() = scopes.removeAt(scopes.size - 1)
    fun declare(name: String, type: TypeName) {
        scopes.last()[name] = type
    }

    fun lookup(name: String): TypeName? {
        for (i in scopes.indices.reversed()) {
            scopes[i][name]?.let { return it }
        }
        return null
    }
}

private fun findFirstReturn(stmts: List<Statement>): ReturnStatement? {
    for (stmt in stmts) {
        when (stmt) {
            is ReturnStatement -> return stmt
            is IfStatement -> {
                findFirstReturn(stmt.thenBranch.body)?.let { return it }
                stmt.elseBranch?.let { b -> findFirstReturn(b.body)?.let { return it } }
            }
            is ForStatement -> findFirstReturn(stmt.body.body)?.let { return it }
            is ForInStatement -> findFirstReturn(stmt.body.body)?.let { return it }
            is WhileStatement -> findFirstReturn(stmt.body.body)?.let { return it }
            is WhenStatement -> {
                stmt.branches.firstNotNullOfOrNull { findFirstReturn(it.body.body) }?.let { return it }
                stmt.elseBranch?.let { b -> findFirstReturn(b.body)?.let { return it } }
            }
            is TryStatement -> {
                findFirstReturn(stmt.tryBlock.body)?.let { return it }
                stmt.catch?.let { c -> findFirstReturn(c.body.body)?.let { return it } }
                stmt.finallyBlock?.let { b -> findFirstReturn(b.body)?.let { return it } }
            }
            else -> {}
        }
    }
    return null
}

/** [paramOrder] reorders named constructor arguments into positional order; [methodNames] tells
 * a `.method()` call on a known instance apart from a plain field access; [hasToString] gates
 * whether a `data class` gets an auto-generated `__tostring`. */
data class ClassInfo(
    val paramOrder: List<String>,
    val methodNames: Set<String>,
    val hasToString: Boolean,
    val methodReturnTypes: Map<String, String> = emptyMap(),
    val propertyTypes: Map<String, String> = emptyMap(),
    val native: Boolean = false,
)

private val BUILTIN_SIGS = mapOf(
    "println" to FunctionSig(emptyList(), TypeName.UNIT),
    "print" to FunctionSig(emptyList(), TypeName.UNIT),
    "sqrt" to FunctionSig(listOf(TypeName.DOUBLE), TypeName.DOUBLE),
    "pow" to FunctionSig(listOf(TypeName.DOUBLE, TypeName.DOUBLE), TypeName.DOUBLE),
)

/** Infers Kotlin-ish types just precisely enough to drive two codegen decisions: Int/Int
 * division truncation and resolving an unannotated function's return type. Deliberately not a
 * real type checker — there's no notion of a type error here. */
class TypeAnalyzer {
    val env = TypeEnv()
    private val functionSigs = BUILTIN_SIGS.toMutableMap()
    private val classes = mutableMapOf<String, ClassInfo>()

    private val instanceScopes = mutableListOf(mutableMapOf<String, String>())

    /** Which class (if any) a `listOf(...)`'s *elements* are all known to be — lets a
     * `.forEach`/`.map`/`.filter` lambda's implicit parameter be tracked as that class too. */
    private val listElementScopes = mutableListOf(mutableMapOf<String, String>())

    fun pushScope() {
        env.push()
        instanceScopes.add(mutableMapOf())
        listElementScopes.add(mutableMapOf())
    }

    fun popScope() {
        env.pop()
        instanceScopes.removeAt(instanceScopes.size - 1)
        listElementScopes.removeAt(listElementScopes.size - 1)
    }

    /** No-op if [className] isn't actually a registered class. */
    fun declareInstance(name: String, className: String) {
        if (className in classes) instanceScopes.last()[name] = className
    }

    fun instanceClassOf(name: String): String? {
        for (i in instanceScopes.indices.reversed()) {
            instanceScopes[i][name]?.let { return it }
        }
        return null
    }

    fun declareListElement(name: String, className: String) {
        if (className in classes) listElementScopes.last()[name] = className
    }

    fun listElementClassOf(name: String): String? {
        for (i in listElementScopes.indices.reversed()) {
            listElementScopes[i][name]?.let { return it }
        }
        return null
    }

    fun classInfo(name: String): ClassInfo? = classes[name]

    /** Pre-registers a class declared in another file of the same [transpileProject] call, so a
     * subclass in this file can still inherit its cross-file superclass's methods. */
    fun registerExternalClass(name: String, info: ClassInfo) {
        classes[name] = info
    }

    fun functionReturnClass(name: String): String? = functionSigs[name]?.rawReturnType?.takeIf { it in classes }

    fun methodReturnClass(className: String, methodName: String): String? =
        classes[className]?.methodReturnTypes?.get(methodName)?.takeIf { it in classes }

    /** [FunctionDecl.rawReturnType] if explicit; otherwise, for an expression-bodied
     * function/method whose body is directly a constructor call, that callee — a best-effort
     * fallback, not real inference. Internal (not private) so [ProjectTranspile]'s cross-file
     * pre-pass can reuse it. */
    internal fun rawReturnTypeOf(fn: FunctionDecl): String {
        if (fn.rawReturnType.isNotEmpty()) return fn.rawReturnType
        val body = fn.body as? ExprBody ?: return ""
        return (body.expr as? CallExpr)?.callee ?: ""
    }

    fun collectSignatures(body: List<Statement>) {
        for (stmt in body) {
            when (stmt) {
                is FunctionDecl -> {
                    val params = stmt.params.map { it.type }
                    functionSigs[stmt.name] = FunctionSig(params, resolveReturnType(stmt), rawReturnTypeOf(stmt))
                }
                is ClassDecl -> {
                    val superInfo = stmt.superClass?.let { classes[it] }
                    classes[stmt.name] = ClassInfo(
                        paramOrder = stmt.params.map { it.name },
                        methodNames = stmt.methods.map { it.name }.toSet() + (superInfo?.methodNames ?: emptySet()),
                        hasToString = stmt.methods.any { it.name == "toString" } || superInfo?.hasToString == true,
                        methodReturnTypes = (superInfo?.methodReturnTypes ?: emptyMap()) +
                            stmt.methods.associate { it.name to rawReturnTypeOf(it) },
                        propertyTypes = (superInfo?.propertyTypes ?: emptyMap()) +
                            stmt.properties.associate { it.name to it.rawType },
                    )
                }
                else -> {}
            }
        }
    }

    private fun resolveReturnType(fn: FunctionDecl): TypeName {
        fn.returnType?.let { return it }
        env.push()
        fn.params.forEach { env.declare(it.name, it.type) }
        val result = when (val body = fn.body) {
            is ExprBody -> inferExprType(body.expr)
            is BlockBody -> findFirstReturn(body.block.body)?.expr?.let { inferExprType(it) } ?: TypeName.UNIT
        }
        env.pop()
        return result
    }

    fun inferExprType(expr: Expression): TypeName = when (expr) {
        is IntLiteral -> TypeName.INT
        is DoubleLiteral -> TypeName.DOUBLE
        is BooleanLiteral -> TypeName.BOOLEAN
        NullLiteral -> TypeName.ANY
        is StringTemplateLiteral -> TypeName.STRING
        is Identifier -> env.lookup(expr.name) ?: TypeName.ANY
        is ParenExpr -> inferExprType(expr.expr)
        is UnaryExpr -> if (expr.op == "!") TypeName.BOOLEAN else inferExprType(expr.expr)
        is BinaryExpr -> inferBinaryType(expr)
        is CallExpr -> inferCallType(expr)
        is PropertyAccessExpr -> TypeName.ANY
        is MethodCallExpr -> TypeName.ANY
        is FunctionRef -> TypeName.ANY
        is IfExpr -> combineBranchTypes(inferExprType(expr.thenExpr), inferExprType(expr.elseExpr))
        is WhenExpr -> {
            val resultTypes = expr.branches.map { inferExprType(it.result) } + inferExprType(expr.elseResult)
            resultTypes.reduce { a, b -> combineBranchTypes(a, b) }
        }
        is ElvisExpr -> combineBranchTypes(inferExprType(expr.left), inferExprType(expr.right))
        is SafeChainExpr -> TypeName.ANY
        is NotNullAssertExpr -> inferExprType(expr.expr)
        is LambdaExpr -> TypeName.ANY
        is IndexExpr -> TypeName.ANY
        is ToPairExpr -> TypeName.ANY
    }

    private fun combineBranchTypes(t1: TypeName, t2: TypeName): TypeName = when {
        t1 == t2 -> t1
        (t1 == TypeName.INT && t2 == TypeName.DOUBLE) || (t1 == TypeName.DOUBLE && t2 == TypeName.INT) -> TypeName.DOUBLE
        else -> t1
    }

    private fun inferBinaryType(expr: BinaryExpr): TypeName {
        if (expr.op in setOf("==", "!=", "<", ">", "<=", ">=", "&&", "||")) return TypeName.BOOLEAN
        val lt = inferExprType(expr.left)
        val rt = inferExprType(expr.right)
        if (expr.op == "+" && (lt == TypeName.STRING || rt == TypeName.STRING)) return TypeName.STRING
        if (lt == TypeName.DOUBLE || rt == TypeName.DOUBLE) return TypeName.DOUBLE
        return TypeName.INT
    }

    private fun inferCallType(expr: CallExpr): TypeName {
        if (expr.callee == "abs") return inferExprType(expr.args[0].toExpression())
        if (expr.callee == "max" || expr.callee == "min") {
            val t0 = inferExprType(expr.args[0].toExpression())
            val t1 = inferExprType(expr.args[1].toExpression())
            return if (t0 == TypeName.INT && t1 == TypeName.INT) TypeName.INT else TypeName.DOUBLE
        }
        return functionSigs[expr.callee]?.returnType ?: TypeName.ANY
    }
}
