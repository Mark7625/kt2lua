package kt2lua.transpiler

import kt2lua.transpiler.emitter.ClassEmitter
import kt2lua.transpiler.emitter.ForEmitter
import kt2lua.transpiler.emitter.IfEmitter
import kt2lua.transpiler.emitter.ObjectEmitter
import kt2lua.transpiler.emitter.ThrowEmitter
import kt2lua.transpiler.emitter.TryEmitter
import kt2lua.transpiler.emitter.WhenEmitter
import kt2lua.transpiler.emitter.WhileEmitter

private fun formatDouble(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) {
        "${value.toLong()}.0"
    } else {
        value.toString()
    }

private fun escapeLuaString(text: String, quote: Char): String {
    val sb = StringBuilder()
    for (c in text) {
        when (c) {
            '\\' -> sb.append("\\\\")
            quote -> sb.append('\\').append(quote)
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/** [QuoteStyle.SMART] double-quotes by default, switching to single quotes only when the text
 * contains a `"` but no `'` (avoids escaping). */
private fun quoteLuaString(text: String, style: QuoteStyle): String {
    val quote = when (style) {
        QuoteStyle.DOUBLE -> '"'
        QuoteStyle.SINGLE -> '\''
        QuoteStyle.SMART -> if (text.contains('"') && !text.contains('\'')) '\'' else '"'
    }
    return "$quote${escapeLuaString(text, quote)}$quote"
}

/** Mirrors Kotlin's Int/Int division, which truncates toward zero (Lua's `/` is always
 * real-number division). Only emitted when the program actually needs it. */
private const val INT_DIV_HELPER = """local function __intdiv(a, b)
  local q = a / b
  if q >= 0 then
    return math.floor(q)
  else
    return math.ceil(q)
  end
end

"""

/** Backs `.forEach { }`/`.map { }`/`.filter { }` on a `listOf(...)`-style array table — Lua has
 * no equivalent stdlib functions. Kept in declaration order (not a map) so output is
 * deterministic. */
private val COLLECTION_HELPERS = listOf(
    "forEach" to """local function __forEach(t, fn)
  for _, v in ipairs(t) do fn(v) end
end

""",
    "map" to """local function __map(t, fn)
  local result = {}
  for _, v in ipairs(t) do table.insert(result, fn(v)) end
  return result
end

""",
    "filter" to """local function __filter(t, fn)
  local result = {}
  for _, v in ipairs(t) do
    if fn(v) then table.insert(result, v) end
  end
  return result
end

""",
    "reduce" to """local function __reduce(t, fn)
  local acc = t[1]
  for i = 2, #t do acc = fn(acc, t[i]) end
  return acc
end

""",
)
private val COLLECTION_HELPER_NAMES = COLLECTION_HELPERS.map { it.first }.toSet()

/** `.fold(initial) { acc, x -> ... }` takes two real arguments, not one, so it's dispatched
 * separately from [COLLECTION_HELPER_NAMES]. */
private const val FOLD_HELPER = """local function __fold(t, initial, fn)
  local acc = initial
  for _, v in ipairs(t) do acc = fn(acc, v) end
  return acc
end

"""

class Emitter(override val style: EmitStyle = EmitStyle.DEFAULT, private val bindings: LuaBindings = LuaBindings()) : EmitContext {
    override val types = TypeAnalyzer()
    private val indentUnit = style.indentUnit
    // var, not val: emitLambda temporarily swaps this out for a fresh buffer so a lambda's
    // body can be captured back into a single inline string.
    private var out = StringBuilder()
    private var indentLevel = 0
    private var usesIntDiv = false
    private var usesFold = false
    private val usedCollectionHelpers = mutableSetOf<String>()

    /** A file with a top-level `fun main()` compiles as a script. A file with no `main()` but
     * at least one bare top-level function compiles as a *module*: every non-`private`
     * top-level function becomes an `M.<name>` field and the chunk ends with `return M`. A file
     * with neither just emits its statements directly.
     *
     * [externalClasses] lets a multi-file caller pre-register classes declared in other files,
     * so a subclass here can still inherit a cross-file superclass's methods. */
    fun emitProgram(program: Program, externalClasses: Map<String, ClassInfo> = emptyMap()): String {
        bindings.register(types)
        for ((name, info) in externalClasses) types.registerExternalClass(name, info)
        types.collectSignatures(program.body)
        val hasMain = program.body.any { it is FunctionDecl && it.name == "main" }
        val hasBareFunctions = program.body.any { it is FunctionDecl }
        when {
            hasMain -> {
                emitTopLevelStatements(program.body)
                line("main()")
            }
            hasBareFunctions -> emitModuleProgram(program.body)
            else -> emitTopLevelStatements(program.body)
        }
        val body = out.toString()
        var prefix = ""
        for ((name, source) in COLLECTION_HELPERS) if (name in usedCollectionHelpers) prefix += source
        if (usesFold) prefix += FOLD_HELPER
        if (usesIntDiv) prefix += INT_DIV_HELPER
        return prefix + body
    }

    private fun emitModuleProgram(body: List<Statement>) {
        line("local M = {}")
        body.forEachIndexed { i, stmt ->
            if (i > 0 && isDeclBoundary(stmt)) line("")
            emitModuleMember(stmt, "M")
        }
        line("return M")
    }

    override fun emitModuleMember(stmt: Statement, moduleVar: String) {
        when {
            stmt is FunctionDecl && stmt.isPrivate -> emitFunctionDecl(stmt)
            stmt is FunctionDecl -> emitModuleFunctionDecl(stmt, moduleVar)
            stmt is VariableDecl -> emitTopLevelVariableDecl(stmt, moduleVar)
            else -> emitStatement(stmt)
        }
    }

    override fun isDeclBoundary(stmt: Statement): Boolean =
        style.blankLineBeforeDeclarations && (stmt is FunctionDecl || stmt is ClassDecl || stmt is ObjectDecl)

    private fun emitTopLevelStatements(body: List<Statement>) {
        body.forEachIndexed { i, stmt ->
            if (i > 0 && isDeclBoundary(stmt)) line("")
            emitTopLevelStatement(stmt)
        }
    }

    /** A non-`private` top-level `val`/`var` becomes both a plain local and a field on the
     * enclosing module table or `object`, so other files can `require(...)` it. */
    private fun emitTopLevelVariableDecl(decl: VariableDecl, moduleVar: String) {
        emitVariableDecl(decl)
        if (!decl.isPrivate) line("$moduleVar.${decl.name} = ${decl.name}")
    }

    private fun isLengthProperty(name: String): Boolean = name == "size" || name == "length"

    override fun line(text: String) {
        out.append(indentUnit.repeat(indentLevel)).append(text).append('\n')
    }

    override fun pushIndent() {
        indentLevel++
    }

    override fun popIndent() {
        indentLevel--
    }

    private fun emitTopLevelStatement(stmt: Statement) {
        if (stmt is FunctionDecl) emitFunctionDecl(stmt) else emitStatement(stmt)
    }

    override fun emitStatement(stmt: Statement) {
        when (stmt) {
            is ImportStatement -> {
                line("local ${stmt.alias} = require(\"${stmt.path}\")")
                types.env.declare(stmt.alias, TypeName.ANY)
            }
            is ObjectDecl -> ObjectEmitter.emitObjectDecl(stmt, this)
            is ClassDecl -> ClassEmitter.emitClassDecl(stmt, this)
            is FunctionDecl -> emitFunctionDecl(stmt)
            is VariableDecl -> emitVariableDecl(stmt)
            is ExpressionStatement -> line(emitExpression(stmt.expr))
            is ReturnStatement -> line(stmt.expr?.let { "return ${emitExpression(it)}" } ?: "return")
            is IfStatement -> IfEmitter.emitIfStatement(stmt, this)
            is WhenStatement -> WhenEmitter.emitWhenStatement(stmt, this)
            is ForStatement -> ForEmitter.emitForStatement(stmt, this)
            is ForInStatement -> ForEmitter.emitForInStatement(stmt, this)
            is WhileStatement -> WhileEmitter.emitWhileStatement(stmt, this)
            is Assignment -> emitAssignment(stmt)
            is Block -> stmt.body.forEach { emitStatement(it) }
            is ThrowStatement -> ThrowEmitter.emitThrowStatement(stmt, this)
            is TryStatement -> TryEmitter.emitTryStatement(stmt, this)
        }
    }

    private fun emitFunctionDecl(fn: FunctionDecl) {
        emitFunctionWithHeader(fn, "local function ${fn.name}(${paramList(fn)})")
    }

    private fun emitModuleFunctionDecl(fn: FunctionDecl, moduleVar: String) {
        emitFunctionWithHeader(fn, "function $moduleVar.${fn.name}(${paramList(fn)})")
    }

    override fun paramList(fn: FunctionDecl): String = fn.params.joinToString(", ") { if (it.isVararg) "..." else it.name }

    private fun emitFunctionWithHeader(fn: FunctionDecl, header: String) {
        types.pushScope()
        fn.params.forEach {
            types.env.declare(it.name, it.type)
            types.declareInstance(it.name, it.rawType)
        }
        line(header)
        indentLevel++
        emitFunctionBody(fn)
        indentLevel--
        line("end")
        types.popScope()
    }

    override fun emitFunctionBody(fn: FunctionDecl) {
        fn.params.firstOrNull { it.isVararg }?.let { line("local ${it.name} = {...}") }
        when (val body = fn.body) {
            is ExprBody -> line("return ${emitExpression(body.expr)}")
            is BlockBody -> body.block.body.forEach { emitStatement(it) }
        }
    }

    /** `obj.method(...)` compiles to a colon-call only when `obj` is a known instance of a
     * class that declares a method with that name; everything else is a plain dot-call. */
    private val NUMERIC_CAST_NAMES = setOf("toDouble", "toInt", "toFloat", "toLong", "toShort", "toByte")

    private fun emitMethodCallTarget(expr: MethodCallExpr): String {
        // Lua 5.1 has exactly one numeric type, so a numeric cast is a no-op.
        if (expr.args.isEmpty() && expr.methodName in NUMERIC_CAST_NAMES) return emitExpression(expr.obj)
        if (expr.args.isEmpty() && expr.methodName == "toString") return "tostring(${emitExpression(expr.obj)})"
        val receiverPath = expressionPath(expr.obj)
        if (expr.methodName == "new" && receiverPath != null) {
            nativeFactory(receiverPath)?.let { return "$it(${emitArgList(expr.args)})" }
        }
        val className = receiverInstanceClass(expr.obj)
        val nativeMethod = className?.takeIf { types.classInfo(it)?.native == true }?.let { bindings.method(it, expr.methodName) }
        if (nativeMethod != null) {
            val separator = if (nativeMethod.first) ":" else "."
            return "${emitExpression(expr.obj)}$separator${expr.methodName}(${emitArgList(expr.args)})"
        }
        val isKnownMethod = className?.let { types.classInfo(it)?.methodNames?.contains(expr.methodName) } == true
        if (isKnownMethod) return "${emitExpression(expr.obj)}:${expr.methodName}(${emitArgList(expr.args)})"
        // `.let { }` — a scope function, not a real Lua method: call the lambda directly.
        if (expr.methodName == "let" && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) emitLambda(lambdaArg, receiverInstanceClass(expr.obj)) else emitExpression(lambdaArg)
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        // `.also { }` — identical to `.let` but returns the receiver, not the block's result.
        if (expr.methodName == "also" && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) {
                emitLambda(lambdaArg, receiverInstanceClass(expr.obj), returnReceiver = true)
            } else {
                emitExpression(lambdaArg)
            }
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        // `.run { }`/`.apply { }` — receiver-style scope functions: no declared lambda param,
        // referring to the receiver via `this`. Forcing the implicit param to be named `self`
        // (the identifier `this` already lexes to) makes `this.x` resolve inside them.
        if ((expr.methodName == "run" || expr.methodName == "apply") && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) {
                emitLambda(
                    lambdaArg.copy(params = emptyList()),
                    receiverInstanceClass(expr.obj),
                    implicitParamName = "self",
                    returnReceiver = expr.methodName == "apply",
                )
            } else {
                emitExpression(lambdaArg)
            }
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        // `.forEach { }`/`.map { }`/`.filter { }` on a `listOf(...)`-style table — routed to a
        // runtime helper since a plain Lua table has no such field. A user-defined class with
        // a same-named real method (checked above) always wins over this.
        if (expr.args.size == 1 && expr.methodName in COLLECTION_HELPER_NAMES) {
            usedCollectionHelpers.add(expr.methodName)
            val elementClass = (expr.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) emitLambda(lambdaArg, elementClass) else emitExpression(lambdaArg)
            return "__${expr.methodName}(${emitExpression(expr.obj)}, $fnStr)"
        }
        if (expr.methodName == "fold" && expr.args.size == 2) {
            usesFold = true
            val lambdaArg = expr.args[1].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) emitLambda(lambdaArg) else emitExpression(lambdaArg)
            return "__fold(${emitExpression(expr.obj)}, ${emitExpression(expr.args[0].toExpression())}, $fnStr)"
        }
        return "${emitExpression(expr.obj)}.${expr.methodName}(${emitArgList(expr.args)})"
    }

    private fun emitVariableDecl(decl: VariableDecl) {
        val type = decl.varType ?: types.inferExprType(decl.init)
        val initStr = emitExpression(decl.init)
        types.env.declare(decl.name, type)
        types.declareInstance(decl.name, decl.rawType)
        instanceClassOfExpr(decl.init)?.let { types.declareInstance(decl.name, it) }
        listElementClassOfExpr(decl.init)?.let { types.declareListElement(decl.name, it) }
        line("local ${decl.name} = $initStr")
    }

    /** `val team = listOf(Employee(...), Employee(...))` — null unless every element resolves
     * to the same registered class. */
    private fun listElementClassOfExpr(expr: Expression): String? {
        if (expr !is CallExpr || expr.callee != "listOf" || expr.args.isEmpty()) return null
        val elementClasses = expr.args.map { instanceClassOfExpr(it.toExpression()) }.toSet()
        return elementClasses.singleOrNull()
    }

    /** The class [expr] is known to produce an instance of, if any — a direct constructor
     * call, a function known to return a class, or a method known to return one. */
    private fun instanceClassOfExpr(expr: Expression): String? = when (expr) {
        is Identifier -> types.instanceClassOf(expr.name) ?: bindings.global(expr.name)
        is PropertyAccessExpr -> expressionPath(expr)?.let { bindings.global(it) }
            ?: receiverInstanceClass(expr.obj)?.let { receiver ->
                if (types.classInfo(receiver)?.native == true) bindings.property(receiver, expr.name)
                else types.classInfo(receiver)?.propertyTypes?.get(expr.name)
            }
        is ParenExpr -> instanceClassOfExpr(expr.expr)
        is NotNullAssertExpr -> instanceClassOfExpr(expr.expr)
        is CallExpr -> if (types.classInfo(expr.callee) != null) expr.callee else types.functionReturnClass(expr.callee)
        // `.also { }`/`.apply { }` always return their receiver unchanged.
        is MethodCallExpr -> if (expr.methodName == "also" || expr.methodName == "apply") {
            receiverInstanceClass(expr.obj)
        } else {
            if (expr.methodName == "new" && expressionPath(expr.obj)?.let { nativeFactory(it) } != null) {
                bindings.type(expressionPath(expr.obj)!!)
            } else receiverInstanceClass(expr.obj)?.let { receiverClass ->
                if (types.classInfo(receiverClass)?.native == true) bindings.method(receiverClass, expr.methodName)?.second
                else types.methodReturnClass(receiverClass, expr.methodName)
            }
        }
        else -> null
    }

    /** Like [instanceClassOfExpr], but also checks whether [expr] is itself an already-tracked
     * variable — needed for a scope-function receiver that may be a fresh expression or a
     * previously-declared instance. */
    private fun receiverInstanceClass(expr: Expression): String? =
        (expr as? Identifier)?.let { types.instanceClassOf(it.name) } ?: instanceClassOfExpr(expr)

    private fun expressionPath(expr: Expression): String? = when (expr) {
        is Identifier -> expr.name
        is PropertyAccessExpr -> expressionPath(expr.obj)?.let { "$it.${expr.name}" }
        else -> null
    }

    private fun nativeFactory(path: String): String? =
        bindings.factory(path)?.takeIf { types.classInfo(path)?.native != false && types.env.lookup(path) == null }

    private fun emitAssignment(stmt: Assignment) {
        val targetStr = emitAssignTarget(stmt.target)
        if (stmt.op == "=") {
            line("$targetStr = ${emitExpression(stmt.expr)}")
            if (stmt.target is NameTarget) {
                instanceClassOfExpr(stmt.expr)?.let { types.declareInstance(stmt.target.name, it) }
            }
            return
        }
        val binOp = stmt.op.dropLast(1)
        val rhsStr = emitExpression(stmt.expr)
        val targetType = (stmt.target as? NameTarget)?.let { types.env.lookup(it.name) }
        if (binOp == "/" && targetType == TypeName.INT && types.inferExprType(stmt.expr) == TypeName.INT) {
            usesIntDiv = true
            line("$targetStr = __intdiv($targetStr, $rhsStr)")
        } else {
            line("$targetStr = $targetStr $binOp ($rhsStr)")
        }
    }

    private fun emitAssignTarget(target: AssignTarget): String = when (target) {
        is NameTarget -> target.name
        is PropertyTarget -> emitPropertyAccess(target.obj, target.name)
        is IndexTarget -> {
            val indexStr = emitExpression(target.index)
            if (types.inferExprType(target.index) == TypeName.STRING) {
                "${emitExpression(target.obj)}[$indexStr]"
            } else {
                "${emitExpression(target.obj)}[($indexStr) + 1]"
            }
        }
    }

    override fun emitExpression(expr: Expression): String = when (expr) {
        is IntLiteral -> expr.value.toString()
        is DoubleLiteral -> formatDouble(expr.value)
        is BooleanLiteral -> expr.value.toString()
        NullLiteral -> "nil"
        is Identifier -> if (types.env.lookup(expr.name) == null && types.instanceClassOf(expr.name) == null && types.classInfo(expr.name)?.native != false)
            bindings.luaName(expr.name) ?: expr.name else expr.name
        is ParenExpr -> "(${emitExpression(expr.expr)})"
        is UnaryExpr -> if (expr.op == "!") "(not ${emitExpression(expr.expr)})" else "(-${emitExpression(expr.expr)})"
        is StringTemplateLiteral -> emitTemplate(expr)
        is BinaryExpr -> emitBinary(expr)
        is CallExpr -> emitCall(expr)
        is PropertyAccessExpr -> if (isLengthProperty(expr.name)) "#${emitExpression(expr.obj)}" else emitPropertyAccess(expr.obj, expr.name)
        is MethodCallExpr -> emitMethodCallTarget(expr)
        is FunctionRef -> expr.name
        is IfExpr -> "(${emitExpression(expr.cond)} and ${emitExpression(expr.thenExpr)} or ${emitExpression(expr.elseExpr)})"
        is WhenExpr -> WhenEmitter.emitWhenExpr(expr, this)
        is ElvisExpr -> emitElvis(expr)
        is SafeChainExpr -> emitSafeChain(expr)
        is NotNullAssertExpr -> emitNotNullAssert(expr)
        is LambdaExpr -> emitLambda(expr)
        is IndexExpr -> emitIndex(expr)
        is ToPairExpr -> "{first = ${emitExpression(expr.first)}, second = ${emitExpression(expr.second)}}"
    }

    private fun emitPropertyAccess(receiver: Expression, name: String): String {
        expressionPath(PropertyAccessExpr(receiver, name))?.let { path ->
            if (types.classInfo(path)?.native != false) bindings.luaName(path)?.let { return it }
        }
        val target = emitExpression(receiver)
        val reserved = setOf("and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while")
        return if (name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && name !in reserved) "$target.$name"
        else "$target[${quoteLuaString(name, style.quoteStyle)}]"
    }

    /** `arr[i]` -> `arr[(i) + 1]` for a *list* index, but `m["key"]` stays unshifted for a *map*
     * key — decided by the index expression's own inferred type, since there's no real type
     * tracking distinguishing a List from a Map. */
    private fun emitIndex(expr: IndexExpr): String {
        val indexStr = emitExpression(expr.index)
        return if (types.inferExprType(expr.index) == TypeName.STRING) {
            "${emitExpression(expr.obj)}[$indexStr]"
        } else {
            "${emitExpression(expr.obj)}[($indexStr) + 1]"
        }
    }

    /** An implicit-param lambda gets a single Lua parameter named [implicitParamName]. Since
     * this must return an inline string like every other expression emitter, it temporarily
     * swaps [out] for a fresh buffer to capture the body, then restores it. [returnReceiver]
     * overrides the usual "last expression is the return value" to always return the implicit
     * parameter instead, for `.also`/`.apply`. */
    private fun emitLambda(
        expr: LambdaExpr,
        elementClass: String? = null,
        implicitParamName: String = "it",
        returnReceiver: Boolean = false,
    ): String {
        val paramNames = expr.params.ifEmpty { listOf(implicitParamName) }
        types.pushScope()
        paramNames.forEach { types.env.declare(it, TypeName.ANY) }
        if (elementClass != null) types.declareInstance(paramNames[0], elementClass)

        val savedOut = out
        val savedIndent = indentLevel
        out = StringBuilder()
        indentLevel = savedIndent + 1
        val stmts = expr.body.body
        stmts.forEachIndexed { i, stmt ->
            if (!returnReceiver && i == stmts.lastIndex && stmt is ExpressionStatement) line("return ${emitExpression(stmt.expr)}")
            else emitStatement(stmt)
        }
        if (returnReceiver) line("return ${paramNames[0]}")
        val bodyLua = out.toString()
        out = savedOut
        indentLevel = savedIndent

        types.popScope()
        return "function(${paramNames.joinToString(", ")})\n${bodyLua}${indentUnit.repeat(savedIndent)}end"
    }

    private fun emitNotNullAssert(expr: NotNullAssertExpr): String =
        "(function()\n" +
            "${indentUnit}local __v = ${emitExpression(expr.expr)}\n" +
            "${indentUnit}if __v == nil then error(\"!! assertion failed: value was null\") end\n" +
            "${indentUnit}return __v\n" +
            "end)()"

    /** An explicit `~= nil` check, not Lua's `a or b` idiom, which would treat `false` the same
     * as `nil`. */
    private fun emitElvis(expr: ElvisExpr): String =
        "(function()\n" +
            "${indentUnit}local __v = ${emitExpression(expr.left)}\n" +
            "${indentUnit}if __v ~= nil then return __v end\n" +
            "${indentUnit}return ${emitExpression(expr.right)}\n" +
            "end)()"

    private fun emitSafeChain(expr: SafeChainExpr): String {
        val sb = StringBuilder("(function()\n")
        sb.append("${indentUnit}local __v = ${emitExpression(expr.base)}\n")
        for (step in expr.steps) {
            val safe = when (step) {
                is ChainProp -> step.safe
                is ChainCall -> step.safe
            }
            if (safe) sb.append("${indentUnit}if __v == nil then return nil end\n")
            when (step) {
                is ChainProp -> sb.append(if (isLengthProperty(step.name)) "${indentUnit}__v = #__v\n" else "${indentUnit}__v = __v.${step.name}\n")
                is ChainCall -> sb.append(emitSafeChainCallStep(step))
            }
        }
        sb.append("${indentUnit}return __v\n")
        sb.append("end)()")
        return sb.toString()
    }

    /** Deliberately simpler than [emitMethodCallTarget] — no class-instance colon-call
     * resolution or collection-helper routing mid-chain, since there's no single receiver
     * expression here to resolve a tracked class against, only the running `__v`. */
    private fun emitSafeChainCallStep(step: ChainCall): String = when {
        step.args.isEmpty() && step.name in NUMERIC_CAST_NAMES -> ""
        step.args.isEmpty() && step.name == "toString" -> "${indentUnit}__v = tostring(__v)\n"
        step.name == "let" && step.args.size == 1 -> {
            val lambdaArg = step.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) emitLambda(lambdaArg) else emitExpression(lambdaArg)
            "${indentUnit}__v = ($fnStr)(__v)\n"
        }
        else -> "${indentUnit}__v = __v.${step.name}(${emitArgList(step.args)})\n"
    }

    private fun emitBinary(expr: BinaryExpr): String {
        val leftStr = emitExpression(expr.left)
        val rightStr = emitExpression(expr.right)
        return when (expr.op) {
            "&&" -> "($leftStr and $rightStr)"
            "||" -> "($leftStr or $rightStr)"
            "!=" -> "($leftStr ~= $rightStr)"
            "/" -> {
                val lt = types.inferExprType(expr.left)
                val rt = types.inferExprType(expr.right)
                if (lt == TypeName.INT && rt == TypeName.INT) {
                    usesIntDiv = true
                    "__intdiv($leftStr, $rightStr)"
                } else {
                    "($leftStr / $rightStr)"
                }
            }
            else -> "($leftStr ${expr.op} $rightStr)"
        }
    }

    private fun emitCall(expr: CallExpr): String {
        if (expr.callee == "listOf") {
            return "{${expr.args.joinToString(", ") { emitExpression(it.toExpression()) }}}"
        }
        if (expr.callee == "emptyList" && expr.args.isEmpty()) {
            return "{}"
        }
        // `mapOf(k1 to v1, k2 to v2)` -> `{[k1] = v1, [k2] = v2}`.
        if (expr.callee == "mapOf" && expr.args.all { it.toExpression() is ToPairExpr }) {
            val entries = expr.args.joinToString(", ") {
                val pair = it.toExpression() as ToPairExpr
                "[${emitExpression(pair.first)}] = ${emitExpression(pair.second)}"
            }
            return "{$entries}"
        }
        // `with(x) { body }` — the one scope function that isn't a method call. Same
        // receiver-style `self`-binding as `.run`.
        if (expr.callee == "with" && expr.args.size == 2) {
            val receiver = expr.args[0].toExpression()
            val lambdaArg = expr.args[1].toExpression()
            if (lambdaArg is LambdaExpr) {
                val fnStr = emitLambda(lambdaArg.copy(params = emptyList()), receiverInstanceClass(receiver), implicitParamName = "self")
                return "($fnStr)(${emitExpression(receiver)})"
            }
        }
        nativeFactory(expr.callee)?.let { return "$it(${emitArgList(expr.args)})" }
        types.classInfo(expr.callee)?.takeUnless { it.native }?.let { return emitConstructorCall(expr, it) }
        if (expr.args.isNotEmpty() && expr.args.all { it is NamedArg } && expr.callee.first().isUpperCase()) {
            // A PascalCase callee with all-named args reads as an object-literal stand-in
            // (e.g. `IdLookup(label = ..., value = ...)`) — emit the bare table.
            return emitArgList(expr.args)
        }
        val positional = expr.args.map { emitExpression(it.toExpression()) }
        return when (expr.callee) {
            "println" -> "print(${positional.joinToString(", ")})"
            "print" -> "io.write(tostring(${positional.getOrElse(0) { "\"\"" }}))"
            "sqrt" -> "math.sqrt(${positional[0]})"
            "abs" -> "math.abs(${positional[0]})"
            "pow" -> "(${positional[0]} ^ ${positional[1]})"
            "max" -> "math.max(${positional[0]}, ${positional[1]})"
            "min" -> "math.min(${positional[0]}, ${positional[1]})"
            else -> "${expr.callee}(${emitArgList(expr.args)})"
        }
    }

    /** Named args are reordered into the constructor's declared parameter order; this subset
     * has no default constructor arguments, so a missing one is always an error. */
    private fun emitConstructorCall(expr: CallExpr, info: ClassInfo): String {
        val positional = expr.args.takeWhile { it is PositionalArg }.map { emitExpression(it.toExpression()) }.toMutableList()
        val namedRest = expr.args.drop(positional.size).filterIsInstance<NamedArg>().associateBy { it.name }
        for (pname in info.paramOrder.drop(positional.size)) {
            val arg = namedRest[pname]
                ?: error("Missing constructor argument '$pname' for ${expr.callee} (default constructor arguments aren't supported in this subset)")
            positional.add(emitExpression(arg.expr))
        }
        return "${expr.callee}.new(${positional.joinToString(", ")})"
    }

    /** An all-named argument list compiles to a single Lua table-literal argument instead of
     * positional args — what this subset uses for a TS-style object-literal argument. */
    override fun emitArgList(args: List<CallArg>): String {
        if (args.isNotEmpty() && args.all { it is NamedArg }) {
            return emitNamedArgTable(args.filterIsInstance<NamedArg>())
        }
        return args.joinToString(", ") { if (it is SpreadArg) "unpack(${emitExpression(it.expr)})" else emitExpression(it.toExpression()) }
    }

    /** Stays on one line when every field's value is single-line; if any value spans multiple
     * lines (e.g. a lambda with a block body), the whole table reflows one field per line. */
    private fun emitNamedArgTable(args: List<NamedArg>): String {
        val baseIndent = indentLevel
        val fields = args.map { arg ->
            indentLevel = baseIndent + 1
            val value = emitExpression(arg.expr)
            indentLevel = baseIndent
            "${arg.name} = $value"
        }
        if (fields.none { it.contains('\n') }) return "{${fields.joinToString(", ")}}"
        val fieldPrefix = indentUnit.repeat(baseIndent + 1)
        val closePrefix = indentUnit.repeat(baseIndent)
        return "{\n" + fields.joinToString(",\n") { "$fieldPrefix$it" } + "\n$closePrefix}"
    }

    private fun emitTemplate(lit: StringTemplateLiteral): String {
        if (lit.parts.isEmpty()) return "\"\""
        val fragments = lit.parts.map { part ->
            when (part) {
                is TemplateExprPart.Text -> quoteLuaString(part.text, style.quoteStyle)
                is TemplateExprPart.Expr -> "tostring(${emitExpression(part.expr)})"
            }
        }
        return if (fragments.size == 1) fragments[0] else fragments.joinToString(" .. ")
    }
}
