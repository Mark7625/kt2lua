package kt2lua.transpiler

data class FunctionSig(val params: List<TypeName>, val returnType: TypeName, val rawReturnType: String = "", val paramNames: List<String> = emptyList(), val defaultedParams: Set<String> = emptySet())

/** Scoped variable-type lookup — push/pop around each Lua block [Emitter] opens, so a type
 * doesn't leak past the scope it's declared in. */
class TypeEnv {
    private val scopes = mutableListOf(mutableMapOf<String, TypeName>())
    fun push() = scopes.add(mutableMapOf())
    fun pop() = scopes.removeAt(scopes.size - 1)
    fun declare(name: String, type: TypeName) {
        scopes.last()[name] = type
    }

    /** Index of the nearest scope that declares [name], or -1. */
    fun declaredScopeIndex(name: String): Int {
        for (i in scopes.indices.reversed()) {
            if (name in scopes[i]) return i
        }
        return -1
    }

    val currentScopeIndex: Int get() = scopes.size - 1

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
    val superClass: String? = null,
    val isData: Boolean = false,
)

/** Every Kotlin spelling of a stdlib collection type collapses to one of these three
 * pseudo-classes — `List`/`Set`/`Map` — so `: MutableSet<Int>`, `: HashMap<String, Int>`, etc.
 * all get tracked the same way as `setOf(...)`/`mapOf(...)` results. */
private val COLLECTION_TYPE_ALIASES = mapOf(
    "List" to "List", "MutableList" to "List", "ArrayList" to "List",
    "Set" to "Set", "MutableSet" to "Set", "HashSet" to "Set", "LinkedHashSet" to "Set",
    "Map" to "Map", "MutableMap" to "Map", "HashMap" to "Map", "LinkedHashMap" to "Map",
    // A range value is the Lua list of its integers; a floating-point range is just its two bounds.
    "IntRange" to "List", "LongRange" to "List", "UIntRange" to "List", "ULongRange" to "List",
    "IntProgression" to "List", "LongProgression" to "List", "UIntProgression" to "List", "ULongProgression" to "List",
    "ClosedRange" to "List", "CharRange" to "List", "CharProgression" to "List", "OpenEndRange" to "List", "ClosedFloatingPointRange" to "FloatRange",
)

/** `val callbacks = mapOf(...)` — an unannotated property's [rawType] is empty, so a Map/Set/List
 * constructor call is recognized here instead, straight from its initializer, the same
 * pseudo-class name [TypeAnalyzer.declareInstance]'s alias table would collapse an explicit
 * annotation to. Empty (not tracked) for anything else. */
private fun collectionConstructorKind(init: Expression): String {
    val callee = (init as? CallExpr)?.callee ?: return ""
    return when (callee) {
        "listOf", "mutableListOf", "arrayListOf", "emptyList" -> "List"
        "setOf", "mutableSetOf", "hashSetOf", "emptySet" -> "Set"
        "mapOf", "mutableMapOf", "hashMapOf", "emptyMap" -> "Map"
        else -> ""
    }
}

/** Only Int/Double are surfaced as a raw type for a class member - every other type name stays
 * empty so a String/List-typed property isn't mistaken for a tracked class instance. */
private fun numericRawType(type: TypeName): String = when (type) {
    TypeName.INT -> "Int"
    TypeName.DOUBLE -> "Double"
    else -> ""
}

private val NUMERIC_CAST_RESULT = mapOf(
    "toDouble" to TypeName.DOUBLE, "toFloat" to TypeName.DOUBLE,
    "toInt" to TypeName.INT, "toLong" to TypeName.INT, "toShort" to TypeName.INT, "toByte" to TypeName.INT,
)

private val DOUBLE_MATH_FUNCTIONS = setOf("sqrt", "floor", "ceil", "sin", "cos", "tan", "exp", "log", "log2", "log10", "hypot", "pow",
    "asin", "acos", "atan", "atan2", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "cbrt", "expm1", "ln", "ln1p")

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
    private val receiverScopes = mutableListOf(mutableListOf<Pair<String, String>>())

    fun declareReceiver(name: String, className: String) {
        declareInstance(name, className)
        receiverScopes.last().add(name to className)
    }

    fun currentReceiver(): String? = receiverScopes.asReversed().firstNotNullOfOrNull { it.lastOrNull()?.first }

    /** Lexical locals win; otherwise the nearest Kotlin receiver that owns the member wins. */
    fun memberReceiver(name: String): String? {
        if (env.lookup(name) != null) return null
        return receiverScopes.asReversed().flatMap { it.asReversed() }.firstOrNull { (_, type) ->
            classes[type]?.let { name in it.propertyTypes || name in it.methodReturnTypes || name in it.methodNames } == true
        }?.first
    }

    /** Which class (if any) a `listOf(...)`'s *elements* are all known to be — lets a
     * `.forEach`/`.map`/`.filter` lambda's implicit parameter be tracked as that class too. */
    private val listElementScopes = mutableListOf(mutableMapOf<String, String>())

    fun pushScope() {
        env.push()
        instanceScopes.add(mutableMapOf())
        receiverScopes.add(mutableListOf())
        listElementScopes.add(mutableMapOf())
    }

    fun popScope() {
        env.pop()
        instanceScopes.removeAt(instanceScopes.size - 1)
        receiverScopes.removeAt(receiverScopes.size - 1)
        listElementScopes.removeAt(listElementScopes.size - 1)
    }

    /** No-op if [className] (after collapsing a collection alias like `MutableSet`/`HashMap`
     * to its canonical `Set`/`Map`/`List` pseudo-class) isn't actually a registered class. */
    fun declareInstance(name: String, className: String) {
        val canonical = COLLECTION_TYPE_ALIASES[className] ?: className
        if (canonical in classes) instanceScopes.last()[name] = canonical
    }

    fun instanceClassOf(name: String): String? {
        for (i in instanceScopes.indices.reversed()) {
            instanceScopes[i][name]?.let { return it }
        }
        return null
    }

    fun forgetInstance(name: String) { instanceScopes.last().remove(name) }

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

    fun functionDefaultedParams(name: String): Set<String> = functionSigs[name]?.defaultedParams ?: emptySet()

    fun functionParamNames(name: String): List<String>? = functionSigs[name]?.paramNames?.takeIf { it.isNotEmpty() }

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
                    functionSigs[stmt.name] = FunctionSig(params, resolveReturnType(stmt), rawReturnTypeOf(stmt), stmt.params.map { it.name }, stmt.params.filter { it.default != null }.mapTo(mutableSetOf()) { it.name })
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
                            stmt.params.associate { it.name to numericRawType(it.type) } +
                            stmt.properties.associate {
                                it.name to it.rawType.ifEmpty {
                                    collectionConstructorKind(it.init).ifEmpty { numericRawType(it.varType ?: inferExprType(it.init)) }
                                }
                            },
                        superClass = stmt.superClass,
                        isData = stmt.isData,
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
        is CharLiteral -> TypeName.CHAR
        NullLiteral -> TypeName.ANY
        is StringTemplateLiteral -> TypeName.STRING
        is Identifier -> env.lookup(expr.name) ?: if (expr.name == "PI" || expr.name == "E") TypeName.DOUBLE else TypeName.ANY
        is ParenExpr -> inferExprType(expr.expr)
        is UnaryExpr -> if (expr.op == "!") TypeName.BOOLEAN else inferExprType(expr.expr)
        is BinaryExpr -> inferBinaryType(expr)
        is CallExpr -> inferCallType(expr)
        is PropertyAccessExpr -> classOfExpr(expr.obj)?.let { classes[it]?.propertyTypes?.get(expr.name) }?.let(::normalizeType)
            ?: when {
                classOfExpr(expr.obj) != null -> TypeName.ANY
                expr.name == "absoluteValue" -> inferExprType(expr.obj)
                expr.name == "code" -> TypeName.INT
                expr.name == "ulp" -> TypeName.DOUBLE
                else -> TypeName.ANY
            }
        is MethodCallExpr -> inferMethodCallType(expr)
        is FunctionRef -> TypeName.ANY
        is IfExpr -> combineBranchTypes(inferExprType(expr.thenExpr), inferExprType(expr.elseExpr))
        is WhenExpr -> {
            val resultTypes = expr.branches.map { inferExprType(it.result) } + inferExprType(expr.elseResult)
            resultTypes.reduce { a, b -> combineBranchTypes(a, b) }
        }
        is ElvisExpr -> combineBranchTypes(inferExprType(expr.left), inferExprType(expr.right))
        is ReturnExpr -> TypeName.ANY
        is SafeChainExpr -> TypeName.ANY
        is NotNullAssertExpr -> inferExprType(expr.expr)
        is LambdaExpr -> TypeName.ANY
        is IndexExpr -> TypeName.ANY
        is ToPairExpr -> TypeName.ANY
        is RangeExpr -> TypeName.ANY
        is IsExpr -> TypeName.BOOLEAN
    }

    private fun classOfExpr(expr: Expression): String? = when (expr) {
        is Identifier -> instanceClassOf(expr.name) ?: "Random".takeIf { expr.name == "Random" && env.lookup("Random") == null && "Random" in classes }
        is PropertyAccessExpr -> "Random".takeIf { expr.name == "Default" && (expr.obj as? Identifier)?.name == "Random" && "Random" in classes }
        is ParenExpr -> classOfExpr(expr.expr)
        is MethodCallExpr -> classOfExpr(expr.obj)?.let {
            if (it == "Sequence" && expr.methodName in SEQUENCE_CHAIN_METHODS) "Sequence" else methodReturnClass(it, expr.methodName)
        }
        is CallExpr -> if (expr.callee in classes) expr.callee
            else if (expr.callee in setOf("sequenceOf", "generateSequence", "emptySequence", "sequence") && "Sequence" in classes) "Sequence"
            else functionReturnClass(expr.callee)
        else -> null
    }

    private fun inferMethodCallType(expr: MethodCallExpr): TypeName {
        if (expr.args.isEmpty()) NUMERIC_CAST_RESULT[expr.methodName]?.let { return it }
        if (expr.args.isEmpty() && (expr.methodName == "roundToInt" || expr.methodName == "roundToLong")) return TypeName.INT
        if (classOfExpr(expr.obj) == null && expr.methodName in setOf("nextUp", "nextDown", "nextTowards", "withSign", "IEEErem")) return TypeName.DOUBLE
        if (expr.args.size == 1 && expr.methodName == "pow" && classOfExpr(expr.obj) == null) return TypeName.DOUBLE
        if (expr.args.isEmpty() && expr.methodName == "toChar" && classOfExpr(expr.obj) == null) return TypeName.CHAR
        if (expr.methodName in setOf("coerceAtLeast", "coerceAtMost", "coerceIn") && classOfExpr(expr.obj) == null) return inferExprType(expr.obj)
        val raw = classOfExpr(expr.obj)?.let { classes[it]?.methodReturnTypes?.get(expr.methodName) } ?: return TypeName.ANY
        return normalizeType(raw)
    }

    private fun combineBranchTypes(t1: TypeName, t2: TypeName): TypeName = when {
        t1 == t2 -> t1
        (t1 == TypeName.INT && t2 == TypeName.DOUBLE) || (t1 == TypeName.DOUBLE && t2 == TypeName.INT) -> TypeName.DOUBLE
        else -> t1
    }

    private fun inferBinaryType(expr: BinaryExpr): TypeName {
        if (expr.op in setOf("==", "!=", "<", ">", "<=", ">=", "&&", "||", "in", "!in")) return TypeName.BOOLEAN
        val lt = inferExprType(expr.left)
        val rt = inferExprType(expr.right)
        if (lt == TypeName.CHAR && expr.op == "+") return TypeName.CHAR
        if (lt == TypeName.CHAR && expr.op == "-") return if (rt == TypeName.CHAR) TypeName.INT else TypeName.CHAR
        if (expr.op == "+" && (lt == TypeName.STRING || rt == TypeName.STRING)) return TypeName.STRING
        if (lt == TypeName.DOUBLE || rt == TypeName.DOUBLE) return TypeName.DOUBLE
        return TypeName.INT
    }

    private fun inferCallType(expr: CallExpr): TypeName {
        val userSig = functionSigs[expr.callee]?.takeIf { it.paramNames.isNotEmpty() }
        if (userSig != null) return userSig.returnType
        if (expr.callee in DOUBLE_MATH_FUNCTIONS) return TypeName.DOUBLE
        if ((expr.callee == "sign" || expr.callee == "round" || expr.callee == "truncate") && expr.args.size == 1) {
            return if (inferExprType(expr.args[0].toExpression()) == TypeName.INT) TypeName.INT else TypeName.DOUBLE
        }
        if (expr.callee == "abs") return inferExprType(expr.args[0].toExpression())
        if (expr.callee == "max" || expr.callee == "min") {
            val t0 = inferExprType(expr.args[0].toExpression())
            val t1 = inferExprType(expr.args[1].toExpression())
            return if (t0 == TypeName.INT && t1 == TypeName.INT) TypeName.INT else TypeName.DOUBLE
        }
        return functionSigs[expr.callee]?.returnType ?: TypeName.ANY
    }
}
