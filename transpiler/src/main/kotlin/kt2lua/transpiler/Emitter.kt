package kt2lua.transpiler

import kt2lua.transpiler.emitter.ClassEmitter
import kt2lua.transpiler.emitter.ForEmitter
import kt2lua.transpiler.emitter.IfEmitter
import kt2lua.transpiler.emitter.ObjectEmitter
import kt2lua.transpiler.emitter.ThrowEmitter
import kt2lua.transpiler.emitter.TryEmitter
import kt2lua.transpiler.emitter.WhenEmitter
import kt2lua.transpiler.emitter.WhileEmitter
import kt2lua.transpiler.stdlib.collections.common.canonicalKeyStr
import kt2lua.transpiler.stdlib.collections.common.structuralEq
import kt2lua.transpiler.stdlib.collections.list.AnyAllNoneOperation
import kt2lua.transpiler.stdlib.collections.list.AssociateByOperation
import kt2lua.transpiler.stdlib.collections.list.AssociateOperation
import kt2lua.transpiler.stdlib.collections.list.AssociateWithOperation
import kt2lua.transpiler.stdlib.collections.list.ChunkedOperation
import kt2lua.transpiler.stdlib.collections.list.CountOperation
import kt2lua.transpiler.stdlib.collections.list.CollectionFilterOperation
import kt2lua.transpiler.stdlib.collections.list.CollectionForEachOperation
import kt2lua.transpiler.stdlib.collections.list.CollectionMapOperation
import kt2lua.transpiler.stdlib.collections.list.DistinctOperation
import kt2lua.transpiler.stdlib.collections.list.DropLastOperation
import kt2lua.transpiler.stdlib.collections.list.DropOperation
import kt2lua.transpiler.stdlib.collections.list.DropWhileOperation
import kt2lua.transpiler.stdlib.collections.list.FilterIndexedOperation
import kt2lua.transpiler.stdlib.collections.list.FilterNotNullOperation
import kt2lua.transpiler.stdlib.collections.list.FindOperation
import kt2lua.transpiler.stdlib.collections.list.FlatMapOperation
import kt2lua.transpiler.stdlib.collections.list.FlattenOperation
import kt2lua.transpiler.stdlib.collections.list.ForEachIndexedOperation
import kt2lua.transpiler.stdlib.collections.list.FoldOperation
import kt2lua.transpiler.stdlib.collections.list.GroupByOperation
import kt2lua.transpiler.stdlib.collections.list.ListAddAllOperation
import kt2lua.transpiler.stdlib.collections.list.ListAddAtOperation
import kt2lua.transpiler.stdlib.collections.list.ListAddOperation
import kt2lua.transpiler.stdlib.collections.list.ListClearOperation
import kt2lua.transpiler.stdlib.collections.list.ListConstructorOperation
import kt2lua.transpiler.stdlib.collections.list.ListFilterLambdaOperation
import kt2lua.transpiler.stdlib.collections.list.ListFilterMembershipOperation
import kt2lua.transpiler.stdlib.collections.list.ListRemoveOperation
import kt2lua.transpiler.stdlib.collections.list.ListReverseOperation
import kt2lua.transpiler.stdlib.collections.list.ListSetOperation
import kt2lua.transpiler.stdlib.collections.list.IndexOfOperation
import kt2lua.transpiler.stdlib.collections.list.IndexOfPredicateOperation
import kt2lua.transpiler.stdlib.collections.list.ListSortOperation
import kt2lua.transpiler.stdlib.collections.list.MapIndexedOperation
import kt2lua.transpiler.stdlib.collections.list.MapNotNullOperation
import kt2lua.transpiler.stdlib.collections.list.MinMaxOperation
import kt2lua.transpiler.stdlib.collections.list.OnEachOperation
import kt2lua.transpiler.stdlib.collections.list.PartitionOperation
import kt2lua.transpiler.stdlib.collections.list.ReduceOperation
import kt2lua.transpiler.stdlib.collections.list.ReversedOperation
import kt2lua.transpiler.stdlib.collections.list.SortedOperation
import kt2lua.transpiler.stdlib.collections.list.SortedWithOperation
import kt2lua.transpiler.stdlib.collections.list.SumOperation
import kt2lua.transpiler.stdlib.collections.list.TakeLastOperation
import kt2lua.transpiler.stdlib.collections.list.TakeOperation
import kt2lua.transpiler.stdlib.collections.list.TakeWhileOperation
import kt2lua.transpiler.stdlib.collections.list.ToListOperation
import kt2lua.transpiler.stdlib.collections.list.ToMapOperation
import kt2lua.transpiler.stdlib.collections.list.ToSetOperation
import kt2lua.transpiler.stdlib.collections.list.WindowedOperation
import kt2lua.transpiler.stdlib.collections.list.ZipOperation
import kt2lua.transpiler.stdlib.collections.list.ZipWithNextOperation
import kt2lua.transpiler.stdlib.collections.map.MapClearOperation
import kt2lua.transpiler.stdlib.collections.map.MapConstructorOperation
import kt2lua.transpiler.stdlib.collections.map.MapFilterOperation
import kt2lua.transpiler.stdlib.collections.map.MapForEachOperation
import kt2lua.transpiler.stdlib.collections.map.MapMapOperation
import kt2lua.transpiler.stdlib.collections.map.MapPutOperation
import kt2lua.transpiler.stdlib.collections.map.MapRemoveOperation
import kt2lua.transpiler.stdlib.collections.map.emitMapPut
import kt2lua.transpiler.stdlib.collections.map.emitMapRemove
import kt2lua.transpiler.stdlib.collections.set.SetAddOperation
import kt2lua.transpiler.stdlib.collections.set.SetClearOperation
import kt2lua.transpiler.stdlib.collections.set.SetConstructorOperation
import kt2lua.transpiler.stdlib.collections.set.SetRemoveOperation
import kt2lua.transpiler.stdlib.collections.set.emitSetAdd
import kt2lua.transpiler.stdlib.collections.set.emitSetRemove
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext
import kt2lua.transpiler.stdlib.registry.OperationCall
import kt2lua.transpiler.stdlib.registry.OperationRegistry

private val RANDOM_METHODS = setOf("nextInt", "nextDouble", "nextBoolean", "nextLong", "nextUInt", "nextULong", "nextUBytes", "asJavaRandom", "asKotlinRandom")

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

private fun quoteLuaString(text: String, style: QuoteStyle): String {
    val quote = when (style) {
        QuoteStyle.DOUBLE -> '"'
        QuoteStyle.SINGLE -> '\''
        QuoteStyle.SMART -> if (text.contains('"') && !text.contains('\'')) '\'' else '"'
    }
    return "$quote${escapeLuaString(text, quote)}$quote"
}

/** Sequence operations that need every element anyway, so they run on the materialized list. */
private val SEQUENCE_VIA_LIST = setOf("joinToString", "partition", "groupBy", "associate", "associateBy", "associateWith", "sumOf", "forEachIndexed")

/** Same, but the result is a Sequence again (sorted() and friends consume everything before producing anything). */
internal val SEQUENCE_VIA_LIST_THEN_SEQUENCE = setOf("sorted", "sortedBy", "sortedByDescending", "sortedDescending", "sortedWith")

/** Sequence operations that write their results into a destination collection and return it. */
private val SEQUENCE_TO_NAMES = setOf(
    "filterTo", "filterNotTo", "filterIndexedTo", "filterIsInstanceTo", "filterNotNullTo", "mapTo", "mapIndexedTo",
    "mapNotNullTo", "mapIndexedNotNullTo", "flatMapTo", "flatMapIndexedTo", "groupByTo", "associateTo", "associateByTo", "associateWithTo",
)

private val SEQUENCE_EXTREME_NAMES = setOf(
    "min", "max", "minOrNull", "maxOrNull", "minBy", "maxBy", "minByOrNull", "maxByOrNull",
    "minOf", "maxOf", "minOfOrNull", "maxOfOrNull", "minWith", "maxWith", "minWithOrNull", "maxWithOrNull",
    "minOfWith", "maxOfWith", "minOfWithOrNull", "maxOfWithOrNull",
)

internal val SEQUENCE_CHAIN_METHODS = setOf(
    "map", "filter", "take", "drop", "takeWhile", "dropWhile", "onEach", "mapNotNull", "filterNotNull", "distinct",
    "filterNot", "mapIndexed", "mapIndexedNotNull", "filterIndexed", "onEachIndexed", "withIndex", "flatMap", "flatMapIndexed",
    "flatten", "zip", "zipWithNext", "windowed", "chunked", "plus", "minus", "plusElement", "minusElement",
    "runningFold", "runningFoldIndexed", "scan", "scanIndexed", "runningReduce", "runningReduceIndexed",
    "distinctBy", "requireNoNulls", "constrainOnce", "orEmpty", "ifEmpty", "filterIsInstance",
) + SEQUENCE_VIA_LIST_THEN_SEQUENCE + setOf("shuffled")

class Emitter(override val style: EmitStyle = EmitStyle.DEFAULT, private val bindings: LuaBindings = LuaBindings()) : EmitContext, CollectionLoweringContext {
    override fun emitSubscriptions(decl: ClassDecl) = kt2lua.transpiler.emitter.SubscriptionEmitter.emit(decl, this, bindings)
    override fun instanceClassOf(expr: Expression): String? = receiverInstanceClass(expr)
    override val types = TypeAnalyzer()

    private val operationRegistry = OperationRegistry().apply {
        for (name in listOf("listOf", "mutableListOf", "arrayListOf")) {
            registerTopLevel(name, ListConstructorOperation(name, parameters = listOf("T")))
        }
        registerTopLevel("emptyList", ListConstructorOperation("emptyList", parameters = emptyList()))
        for (name in listOf("setOf", "mutableSetOf", "hashSetOf")) {
            registerTopLevel(name, SetConstructorOperation(name, parameters = listOf("T")))
        }
        registerTopLevel("emptySet", SetConstructorOperation("emptySet", parameters = emptyList()))
        for (name in listOf("mapOf", "mutableMapOf", "hashMapOf")) {
            registerTopLevel(name, MapConstructorOperation(name, parameters = listOf("Pair<K, V>")))
        }
        registerTopLevel("emptyMap", MapConstructorOperation("emptyMap", parameters = emptyList()))

        listOf(
            ListAddOperation(), ListAddAtOperation(), ListRemoveOperation(), ListSetOperation(), ListClearOperation(), ListAddAllOperation(),
            ListReverseOperation(),
            SetAddOperation(), SetRemoveOperation(), SetClearOperation(),
            MapPutOperation(), MapRemoveOperation(), MapClearOperation(),
            CollectionMapOperation(), CollectionFilterOperation(), MapMapOperation(), MapFilterOperation(),
            CollectionForEachOperation(), MapForEachOperation(),
            FoldOperation(), ReduceOperation(),
            TakeOperation(), DropOperation(), TakeLastOperation(), DropLastOperation(), TakeWhileOperation(), DropWhileOperation(),
            FlatMapOperation(), ChunkedOperation(), OnEachOperation(),
            GroupByOperation(), AssociateOperation(), AssociateByOperation(), AssociateWithOperation(),
            CountOperation(), IndexOfOperation(),
            MapIndexedOperation(), FilterIndexedOperation(), ForEachIndexedOperation(), MapNotNullOperation(), FilterNotNullOperation(),
            PartitionOperation(), FlattenOperation(), ReversedOperation(), ZipOperation(), ZipWithNextOperation(),
            WindowedOperation(), SortedWithOperation(), ToMapOperation(),
        ).forEach(::register)

        // Operation "families" that register multiple parameterized instances own that list themselves
        // (see each class's companion ALL) so this setup doesn't need to know their method-name/flag combinations.
        (
            ListSortOperation.ALL + ListFilterLambdaOperation.ALL + ListFilterMembershipOperation.ALL +
                SortedOperation.ALL + DistinctOperation.ALL + AnyAllNoneOperation.ALL + SumOperation.ALL +
                FindOperation.ALL + IndexOfPredicateOperation.ALL + MinMaxOperation.ALL +
                ToListOperation.ALL + ToSetOperation.ALL
            ).forEach(::register)
    }

    fun stdlibOperationRegistry(): OperationRegistry = operationRegistry

    override fun pushScope() = types.pushScope()
    override fun popScope() = types.popScope()
    override fun declareVar(name: String, type: TypeName) = types.env.declare(name, type)
    override fun declareInstance(name: String, className: String) = types.declareInstance(name, className)
    override fun nextTemp(prefix: String): String = "$prefix${++receiverCounter}"
    override fun listElementClassOf(name: String): String? = types.listElementClassOf(name)
    override fun isNativeClass(className: String): Boolean = types.classInfo(className)?.native != false
    override fun useRuntimeHelper(helper: RuntimeHelper) {
        usedRuntimeHelpers += helper
    }
    private val indentUnit = style.indentUnit
    private var out = StringBuilder()
    private var indentLevel = 0
    private val usedRuntimeHelpers = mutableSetOf<RuntimeHelper>()
    private var receiverCounter = 0
    private val importedModules = mutableSetOf<String>()

    private val inlineAccumulateMethodNames: Set<String> =
        operationRegistry.memberOperations().filter { it.needsResultVar() }.mapTo(mutableSetOf()) { it.id.name }

    // forEach/forEachIndexed are also needsResultVar() == false but go through a dedicated path that
    // threads elementClass to the lambda body; emitInlineMutation's OperationCall doesn't carry that.
    private val mutationMethodNames: Map<String, Set<String>> =
        listOf("List", "Set", "Map").associateWith { kind ->
            operationRegistry.methodNames(kind, needsResultVar = false) - setOf("forEach", "forEachIndexed")
        }

    private fun collectionMethodResultKind(receiverClass: String, expr: MethodCallExpr): String? = when {
        expr.methodName == "asSequence" || expr.methodName == "asStream" -> "Sequence"
        receiverClass == "Sequence" && expr.methodName in SEQUENCE_CHAIN_METHODS -> "Sequence"
        receiverClass == "Sequence" && expr.methodName in setOf("toList", "toMutableList", "asIterable") -> "List"
        receiverClass == "Sequence" && expr.methodName in setOf("toSet", "toMutableSet", "toHashSet", "toSortedSet") -> "Set"
        receiverClass == "Sequence" && expr.methodName == "unzip" -> "Pair"
        expr.methodName == "groupingBy" && receiverClass in setOf("Sequence", "List", "Set") -> "Grouping"
        expr.methodName == "filterIsInstance" && receiverClass in setOf("List", "Set") -> "List"
        expr.methodName == "joinTo" && receiverClass in setOf("Sequence", "List", "Set") -> expr.args.firstOrNull()?.toExpression()?.let { receiverInstanceClass(it) }
        receiverClass == "Sequence" && expr.methodName in SEQUENCE_TO_NAMES -> expr.args.firstOrNull()?.toExpression()?.let { receiverInstanceClass(it) }
        receiverClass == "Sequence" && expr.methodName in SEQUENCE_VIA_LIST ->
            operationRegistry.resolve("List", expr.methodName, OperationCall(expr.obj, expr.args), this)?.resultKind("List")
        expr.methodName == "onEach" -> receiverClass
        else -> operationRegistry.resolve(receiverClass, expr.methodName, OperationCall(expr.obj, expr.args), this)?.resultKind(receiverClass)
    }

    fun emitProgram(program: Program, externalClasses: Map<String, ClassInfo> = emptyMap()): String {
        bindings.register(types)
        types.registerExternalClass(RESULT_CLASS, ClassInfo(emptyList(), emptySet(), false))
        types.registerExternalClass("String", ClassInfo(emptyList(), emptySet(), false, propertyTypes = mapOf("length" to "Int"), native = true))
        types.registerExternalClass("List", ClassInfo(emptyList(), emptySet(), false, propertyTypes = mapOf("size" to "Int"), native = true))
        types.registerExternalClass("Set", ClassInfo(emptyList(), emptySet(), false, propertyTypes = mapOf("size" to "Int"), native = true))
        types.registerExternalClass("Map", ClassInfo(emptyList(), emptySet(), false, propertyTypes = mapOf("size" to "Int"), native = true))
        types.registerExternalClass(
            "Sequence",
            ClassInfo(emptyList(), emptySet(), false, methodReturnTypes = mapOf("average" to "Double", "sumByDouble" to "Double", "sumBy" to "Int"), native = true),
        )
        types.registerExternalClass("Pair", ClassInfo(emptyList(), emptySet(), false, native = true))
        types.registerExternalClass("Grouping", ClassInfo(emptyList(), emptySet(), false, native = true))
        types.registerExternalClass(
            "StringBuilder",
            ClassInfo(
                emptyList(), setOf("append", "appendLine", "isEmpty", "isNotEmpty", "clear"), false,
                propertyTypes = mapOf("length" to "Int"),
                methodReturnTypes = mapOf("append" to "StringBuilder", "appendLine" to "StringBuilder", "clear" to "StringBuilder"),
                native = true,
            ),
        )
        types.registerExternalClass(
            "FloatRange",
            ClassInfo(emptyList(), emptySet(), false, propertyTypes = mapOf("start" to "Double", "endInclusive" to "Double"), native = true),
        )
        types.registerExternalClass(
            "Random",
            ClassInfo(
                emptyList(), RANDOM_METHODS, false,
                methodReturnTypes = mapOf(
                    "nextInt" to "Int", "nextDouble" to "Double", "nextBoolean" to "Boolean", "nextLong" to "Long",
                    "nextUInt" to "Int", "nextULong" to "Long", "nextUBytes" to "List",
                    "asJavaRandom" to "Random", "asKotlinRandom" to "Random",
                ),
                native = true,
            ),
        )
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
        val prefix = RuntimeHelper.entries.filter { it in usedRuntimeHelpers }.joinToString("") { it.source }
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
            stmt is VariableDecl -> {
                emitTopLevelVariableDecl(stmt, moduleVar)
                if (objectName != null) types.instanceClassOf(stmt.name)?.let { objectMemberClasses["$moduleVar.${stmt.name}"] = it }
            }
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

    private fun emitTopLevelVariableDecl(decl: VariableDecl, moduleVar: String) {
        emitVariableDecl(decl)
        if (!decl.isPrivate) line("$moduleVar.${decl.name} = ${decl.name}")
    }

    private fun isLengthProperty(name: String): Boolean = name == "size" || name == "length"

    private fun lengthAccess(receiverStr: String, className: String?): String = when (className) {
        "Set" -> "#$receiverStr.list"
        "Map" -> "#$receiverStr.keys"
        "StringBuilder" -> "#table.concat($receiverStr.buf)"
        else -> "#$receiverStr"
    }

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
                importedModules.add(stmt.alias)
            }
            is ObjectDecl -> ObjectEmitter.emitObjectDecl(stmt, this)
            is ClassDecl -> ClassEmitter.emitClassDecl(stmt, this)
            is FunctionDecl -> emitFunctionDecl(stmt)
            is VariableDecl -> emitVariableDecl(stmt)
            is DestructuringDecl -> emitDestructuringDecl(stmt)
            is ExpressionStatement -> {
                elvisReturn(stmt.expr)?.let { guard ->
                    val temporary = nextTemp("__guard")
                    line("local $temporary = ${emitExpression(guard.left)}")
                    emitReturnGuard(temporary, guard.right as ReturnExpr)
                    return
                }
                val discardExpr = stmt.expr
                if (discardExpr is MethodCallExpr && !receiverOverridesMethod(discardExpr.obj, discardExpr.methodName) &&
                    receiverInstanceClass(discardExpr.obj) != "Sequence"
                ) {
                    val elementClass = (discardExpr.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
                    if (discardExpr.methodName == "forEach" && discardExpr.args.size == 1) {
                        emitForEachStatement(discardExpr.obj, discardExpr.args[0].toExpression(), elementClass)
                        return
                    }
                    if (discardExpr.methodName == "forEachIndexed" && discardExpr.args.size == 1 && discardExpr.args[0].toExpression() is LambdaExpr) {
                        emitForEachIndexedStatement(discardExpr.obj, discardExpr.args[0].toExpression() as LambdaExpr)
                        return
                    }
                    if (emitInlineMutation(discardExpr)) return
                    if (discardExpr.methodName in inlineAccumulateMethodNames) {
                        val discard = nextTemp("__discard")
                        if (emitInlineAccumulate(discardExpr, discard, elementClass)) return
                    }
                }
                val code = emitExpression(stmt.expr)
                val callable = stmt.expr is CallExpr || stmt.expr is MethodCallExpr || stmt.expr is SafeChainExpr || stmt.expr is NotNullAssertExpr
                line(if (!callable) "do local __discard = $code end" else if (code.startsWith("(")) ";$code" else code)
            }
            is ReturnStatement -> {
                val value = stmt.expr?.let { emitExpression(it) }
                if (insideTryClosure) {
                    tryReturnSeen = true
                    line(if (value != null) "return true, $value" else "return true")
                } else {
                    line(if (value != null) "return $value" else "return")
                }
            }
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

    private val objectMemberClasses = mutableMapOf<String, String>()
    private var objectName: String? = null
    private var objectVars: Set<String> = emptySet()
    private var objectFuns: Set<String> = emptySet()
    private var objectScopeIndex: Int = -1

    override fun withObjectScope(decl: ObjectDecl, block: () -> Unit) {
        val saved = listOf(objectName, objectVars, objectFuns, objectScopeIndex)
        types.pushScope()
        objectName = decl.name
        objectVars = decl.body.filterIsInstance<VariableDecl>().filter { !it.isPrivate }.mapTo(mutableSetOf()) { it.name }
        objectFuns = decl.body.filterIsInstance<FunctionDecl>().filter { !it.isPrivate }.mapTo(mutableSetOf()) { it.name }
        objectScopeIndex = types.env.currentScopeIndex
        block()
        types.popScope()
        @Suppress("UNCHECKED_CAST")
        objectName = saved[0] as String?
        @Suppress("UNCHECKED_CAST")
        objectVars = saved[1] as Set<String>
        @Suppress("UNCHECKED_CAST")
        objectFuns = saved[2] as Set<String>
        objectScopeIndex = saved[3] as Int
    }

    private fun objectMemberPrefix(name: String, vars: Set<String>): String? {
        val obj = objectName ?: return null
        if (name !in vars) return null
        val declaredAt = types.env.declaredScopeIndex(name)
        return if (declaredAt == -1 || declaredAt == objectScopeIndex) obj else null
    }

    override var insideTryClosure: Boolean = false
    override var tryReturnSeen: Boolean = false

    override fun emitFunctionBody(fn: FunctionDecl) {
        val savedInsideTry = insideTryClosure
        insideTryClosure = false
        fn.params.firstOrNull { it.isVararg }?.let { line("local ${it.name} = {...}") }
        for (p in fn.params) {
            p.default?.let { line("if ${p.name} == nil then ${p.name} = ${emitExpression(it)} end") }
        }
        when (val body = fn.body) {
            is ExprBody -> line("return ${emitExpression(body.expr)}")
            is BlockBody -> body.block.body.forEach { emitStatement(it) }
        }
        insideTryClosure = savedInsideTry
    }

    private val NUMERIC_CAST_NAMES = setOf("toDouble", "toInt", "toFloat", "toLong", "toShort", "toByte")

    private fun emitStringBuilderCall(expr: MethodCallExpr): String? {
        val sb = emitExpression(expr.obj)
        val a = expr.args.map { it.toExpression() }
        fun appended(value: String) = "(function(b) table.insert(b.buf, $value) return b end)($sb)"
        return when {
            expr.methodName == "append" && a.size == 1 -> appended(formatForToString(a[0], emitExpression(a[0])))
            expr.methodName == "appendLine" && a.isEmpty() -> appended("\"\\n\"")
            expr.methodName == "appendLine" && a.size == 1 -> appended("${formatForToString(a[0], emitExpression(a[0]))} .. \"\\n\"")
            expr.methodName == "isEmpty" && a.isEmpty() -> "(#table.concat($sb.buf) == 0)"
            expr.methodName == "isNotEmpty" && a.isEmpty() -> "(#table.concat($sb.buf) ~= 0)"
            expr.methodName == "clear" && a.isEmpty() -> "(function(b) b.buf = {} return b end)($sb)"
            else -> null
        }
    }

    private fun emitMethodCallTarget(expr: MethodCallExpr): String {
        if (receiverInstanceClass(expr.obj) == "StringBuilder") emitStringBuilderCall(expr)?.let { return it }
        asRange(expr)?.let { return emitRangeExpr(it) }
        if (expr.methodName == "contains" && expr.args.size == 1 && receiverInstanceClass(expr.obj) == "FloatRange") {
            return emitContains(expr.obj, emitExpression(expr.obj), emitExpression(expr.args[0].toExpression()))
        }
        if (expr.methodName in RANDOM_METHODS && receiverInstanceClass(expr.obj) == "Random") {
            if (expr.methodName == "asJavaRandom" || expr.methodName == "asKotlinRandom") return emitExpression(expr.obj)
            val (argStrs, argExprs) = randomArguments(expr.args)
            if (isDefaultRandom(expr.obj)) return emitDefaultRandomCall(expr.methodName, argStrs, argExprs)
            useRuntimeHelper(RuntimeHelper.RANDOM)
            return "${emitExpression(expr.obj)}:${expr.methodName}(${argStrs.joinToString(", ")})"
        }
        if (expr.methodName == "pow" && expr.args.size == 1 && receiverInstanceClass(expr.obj) == null && !hasDeclaredMethod(expr)) {
            return "(${emitExpression(expr.obj)} ^ ${emitExpression(expr.args[0].toExpression())})"
        }
        if (receiverInstanceClass(expr.obj) == null && !hasDeclaredMethod(expr)) {
            emitMathExtensionCall(expr)?.let { return it }
        }
        if (expr.methodName == "invoke" && !hasDeclaredMethod(expr) && receiverInstanceClass(expr.obj) == null) {
            return "${emitExpression(expr.obj)}(${emitArgList(expr.args)})"
        }
        if (expr.args.isEmpty() && expr.methodName in NUMERIC_CAST_NAMES) {
            val inner = emitExpression(expr.obj)
            if (expr.methodName in setOf("toDouble", "toFloat") || types.inferExprType(expr.obj) == TypeName.INT) return inner
            return "(math.modf(tonumber($inner)))"
        }
        if (expr.args.isEmpty() && expr.methodName == "toString") return formatForToString(expr.obj, emitExpression(expr.obj))
        if (expr.args.isEmpty() && expr.methodName == "hashCode") return emitHashCodeExpr(expr.obj)
        val receiverPath = expressionPath(expr.obj)
        receiverPath?.let { bindings.hostPath("$it.${expr.methodName}") }?.let { path ->
            return "$path(${emitArgList(expr.args)})"
        }
        if (expr.methodName == "new" && receiverPath != null) {
            nativeFactory(receiverPath)?.let { return "$it(${emitArgList(expr.args)})" }
        }
        val className = receiverInstanceClass(expr.obj)
        if (className != null && bindings.isDataRecord(className)) {
            if (expr.methodName == "copy") {
                usedRuntimeHelpers += RuntimeHelper.RECORD_COPY
                val changes = recordArguments(className, expr.args).joinToString(", ") {
                    "{key = ${quoteLuaString(bindings.propertyName(className, it.name), style.quoteStyle)}, value = ${emitExpression(it.expr)}}"
                }
                return "__copyRecord(${emitExpression(expr.obj)}, {$changes})"
            }
            if (expr.methodName.matches(Regex("component[1-9][0-9]*")) && expr.args.isEmpty()) {
                val index = expr.methodName.removePrefix("component").toInt() - 1
                val field = bindings.recordFields(className)!!.getOrElse(index) { error("Invalid component index for $className") }
                return emitPropertyAccess(expr.obj, field)
            }
        }
        if (expr.methodName == "copy" && className != null) {
            types.classInfo(className)?.takeIf { !it.native && it.paramOrder.isNotEmpty() }?.let {
                return emitDataClassCopyExpr(className, it, expr.obj, expr.args)
            }
        }
        if (className == RESULT_CLASS) return emitResultMethodCall(expr)
        val nativeMethod = className?.takeIf { types.classInfo(it)?.native == true }?.let { bindings.method(it, expr.methodName) }
        if (nativeMethod != null) {
            val separator = if (nativeMethod.first) ":" else "."
            return "${emitExpression(expr.obj)}$separator${bindings.methodName(className!!, expr.methodName)}(${emitArgList(expr.args)})"
        }
        val isKnownMethod = className?.let { types.classInfo(it)?.methodNames?.contains(expr.methodName) } == true
        if (isKnownMethod) return "${emitExpression(expr.obj)}:${expr.methodName}(${emitArgList(expr.args)})"
        emitScopeFunctionCall(expr)?.let { return it }
        if (expr.methodName in setOf("thenBy", "thenByDescending") && expr.args.size == 1) {
            collectComparatorChain(expr)?.let { return emitComparatorChainExpr(it) }
        }
        val collClass = className
        if (expr.methodName == "contains" && expr.args.size == 1 && collClass != "Map" && collClass != "Sequence" && !isModuleCall(expr.obj)) {
            val receiverStr = emitExpression(expr.obj)
            val valueExpr = expr.args[0].toExpression()
            return emitContains(expr.obj, receiverStr, emitExpression(valueExpr), valueExpr)
        }
        if (expr.args.isEmpty() && expr.methodName in setOf("first", "last", "firstOrNull", "lastOrNull") && collClass != "Map" && collClass != "Sequence" && !isModuleCall(expr.obj)) {
            val receiverStr = iterationArrayOf(expr.obj)
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            return if (expr.methodName == "last" || expr.methodName == "lastOrNull") {
                "(type($receiverStr) == \"string\" and string.sub($receiverStr, -1, -1) or __nilDecode($receiverStr[#$receiverStr]))"
            } else {
                "(type($receiverStr) == \"string\" and string.sub($receiverStr, 1, 1) or __nilDecode($receiverStr[1]))"
            }
        }
        if (expr.methodName == "joinToString" && collClass != "Map" && collClass != "Sequence" && !isModuleCall(expr.obj)) {
            return emitJoinToStringExpr(iterationArrayOf(expr.obj), expr.args)
        }
        if (collClass == "Set") {
            val receiverStr = emitExpression(expr.obj)
            if (expr.methodName == "count" && expr.args.isEmpty()) return "(#$receiverStr.list)"
            if (expr.methodName == "isEmpty" && expr.args.isEmpty()) return "(#$receiverStr.list == 0)"
            if (expr.methodName == "isNotEmpty" && expr.args.isEmpty()) return "(#$receiverStr.list ~= 0)"
            if (expr.methodName in mutationMethodNames.getValue("Set")) {
                emitCollectionIIFE(null) { emitInlineMutation(expr) }?.let { return it }
            }
        }
        if (collClass == "Map") {
            val receiverStr = emitExpression(expr.obj)
            if (expr.methodName == "get" && expr.args.size == 1) {
                val keyExpr = expr.args[0].toExpression()
                return "$receiverStr.map[${canonicalKeyStr(keyExpr, emitExpression(keyExpr))}]"
            }
            if (expr.methodName == "getOrDefault" && expr.args.size == 2) {
                val keyExpr = expr.args[0].toExpression()
                val keyStr = canonicalKeyStr(keyExpr, emitExpression(keyExpr))
                val defaultStr = emitExpression(expr.args[1].toExpression())
                return "(function() local __v = $receiverStr.map[$keyStr]; if __v ~= nil then return __v end; return $defaultStr end)()"
            }
            if (expr.methodName == "containsKey" && expr.args.size == 1) {
                val keyExpr = expr.args[0].toExpression()
                return "($receiverStr.map[${canonicalKeyStr(keyExpr, emitExpression(keyExpr))}] ~= nil)"
            }
            if (expr.methodName == "containsValue" && expr.args.size == 1) {
                val valueExpr = expr.args[0].toExpression()
                val valueStr = emitExpression(valueExpr)
                return "(function() local __r = $receiverStr for _, __k in ipairs(__r.keys) do if ${structuralEq(valueExpr, "__r.map[__k]", valueStr)} then return true end end return false end)()"
            }
            if (expr.methodName == "count" && expr.args.isEmpty()) return "(#$receiverStr.keys)"
            if (expr.methodName == "isEmpty" && expr.args.isEmpty()) return "(#$receiverStr.keys == 0)"
            if (expr.methodName == "isNotEmpty" && expr.args.isEmpty()) return "(#$receiverStr.keys ~= 0)"
            if (expr.methodName in mutationMethodNames.getValue("Map")) {
                emitCollectionIIFE(null) { emitInlineMutation(expr) }?.let { return it }
            }
        }
        if (expr.methodName == "asSequence" && expr.args.isEmpty() && collClass in setOf("List", "Set")) {
            return emitListToSequenceExpr(expr.obj)
        }
        if (collClass == "Sequence" && expr.args.isEmpty() && (expr.methodName == "asSequence" || expr.methodName == "asStream")) {
            // A java.util.stream.Stream is represented as the Sequence it came from, so converting
            // either way is the identity.
            return emitExpression(expr.obj)
        }
        if (collClass in setOf("List", "Set") && expr.methodName == "filterIsInstance" && expr.typeArgs.isNotEmpty()) {
            return emitExpression(MethodCallExpr(MethodCallExpr(MethodCallExpr(expr.obj, "asSequence", emptyList()), "filterIsInstance", expr.args, expr.typeArgs), "toList", emptyList()))
        }
        if (collClass in setOf("List", "Set") && expr.methodName == "joinTo" && expr.args.isNotEmpty()) {
            return emitExpression(MethodCallExpr(MethodCallExpr(expr.obj, "asSequence", emptyList()), "joinTo", expr.args))
        }
        if (collClass in setOf("List", "Set") && expr.methodName == "groupingBy" && expr.args.size == 1) {
            return emitExpression(MethodCallExpr(MethodCallExpr(expr.obj, "asSequence", emptyList()), "groupingBy", expr.args))
        }
        if (receiverInstanceClass(expr.obj) == "Grouping") emitGroupingCall(expr)?.let { return it }
        if (collClass == "Sequence" && expr.methodName in SEQUENCE_VIA_LIST) {
            return emitExpression(MethodCallExpr(MethodCallExpr(expr.obj, "toList", emptyList()), expr.methodName, expr.args))
        }
        if (collClass == "Sequence" && expr.methodName in SEQUENCE_VIA_LIST_THEN_SEQUENCE) {
            return emitExpression(MethodCallExpr(MethodCallExpr(MethodCallExpr(expr.obj, "toList", emptyList()), expr.methodName, expr.args), "asSequence", emptyList()))
        }
        if (collClass == "Sequence") {
            val seqStr = emitExpression(expr.obj)
            val arg0 = expr.args.getOrNull(0)?.toExpression()
            emitSequenceEager(expr, seqStr)?.let { return it }
            when {
                expr.methodName == "map" && arg0 != null -> return emitSequenceMapExpr(expr.obj, arg0)
                expr.methodName == "filter" && arg0 != null -> return emitSequenceFilterExpr(expr.obj, arg0)
                expr.methodName == "mapNotNull" && arg0 != null -> return emitSequenceMapNotNullExpr(expr.obj, arg0)
                expr.methodName == "filterNotNull" && expr.args.isEmpty() -> return emitSequenceFilterNotNullExpr(expr.obj)
                expr.methodName == "onEach" && arg0 != null -> return emitSequenceOnEachExpr(expr.obj, arg0)
                expr.methodName == "take" && arg0 != null -> return emitSequenceTakeExpr(expr.obj, emitExpression(arg0))
                expr.methodName == "drop" && arg0 != null -> return emitSequenceDropExpr(expr.obj, emitExpression(arg0))
                expr.methodName == "takeWhile" && arg0 != null -> return emitSequenceTakeWhileExpr(expr.obj, arg0)
                expr.methodName == "dropWhile" && arg0 != null -> return emitSequenceDropWhileExpr(expr.obj, arg0)
                expr.methodName == "distinct" && expr.args.isEmpty() -> return emitSequenceDistinctExpr(expr.obj)
                expr.methodName == "distinctBy" && arg0 != null -> return emitSequenceDistinctByExpr(expr.obj, arg0)
                expr.methodName == "filterIsInstance" && expr.typeArgs.isNotEmpty() ->
                    return emitSequenceFilterExpr(expr.obj, null, predicateLua = instancePredicate(expr.typeArgs[0]))
                expr.methodName == "groupingBy" && arg0 != null -> return emitSequenceGroupingBy(expr.obj, arg0)
                expr.methodName in SEQUENCE_TO_NAMES && expr.args.isNotEmpty() -> return emitSequenceToVariant(expr, seqStr)
                expr.methodName == "filterNot" && arg0 != null -> return emitSequenceFilterExpr(expr.obj, arg0, negate = true)
                expr.methodName in setOf("mapIndexed", "mapIndexedNotNull", "filterIndexed", "onEachIndexed") && arg0 != null ->
                    return emitSequenceIndexedExpr(expr.methodName, expr.obj, arg0)
                expr.methodName == "withIndex" && expr.args.isEmpty() -> return emitSequenceIndexedExpr("withIndex", expr.obj, null)
                expr.methodName == "flatMap" && arg0 != null -> return emitSequenceFlatMapExpr(expr.obj, arg0, indexed = false)
                expr.methodName == "flatMapIndexed" && arg0 != null -> return emitSequenceFlatMapExpr(expr.obj, arg0, indexed = true)
                expr.methodName == "flatten" && expr.args.isEmpty() -> return emitSequenceFlatMapExpr(expr.obj, null, indexed = false)
                expr.methodName == "zip" && arg0 != null -> return emitSequenceZipExpr(expr.obj, arg0, expr.args.getOrNull(1)?.toExpression())
                expr.methodName == "zipWithNext" -> return emitSequenceZipWithNextExpr(expr.obj, arg0)
                expr.methodName == "windowed" && arg0 != null -> return emitSequenceWindowedExpr(expr)
                expr.methodName == "chunked" && arg0 != null -> return emitSequenceWindowedExpr(expr)
                expr.methodName in setOf("plus", "plusElement") && arg0 != null -> return emitSequencePlusExpr(expr.obj, arg0, expr.methodName == "plusElement")
                expr.methodName in setOf("minus", "minusElement") && arg0 != null -> return emitSequenceMinusExpr(expr.obj, arg0, expr.methodName == "minusElement")
                expr.methodName in setOf("runningFold", "scan") && expr.args.size == 2 ->
                    return emitSequenceRunningExpr(expr.obj, fold = true, indexed = false, expr.args[0].toExpression(), expr.args[1].toExpression())
                expr.methodName in setOf("runningFoldIndexed", "scanIndexed") && expr.args.size == 2 ->
                    return emitSequenceRunningExpr(expr.obj, fold = true, indexed = true, expr.args[0].toExpression(), expr.args[1].toExpression())
                expr.methodName == "runningReduce" && arg0 != null -> return emitSequenceRunningExpr(expr.obj, fold = false, indexed = false, null, arg0)
                expr.methodName == "runningReduceIndexed" && arg0 != null -> return emitSequenceRunningExpr(expr.obj, fold = false, indexed = true, null, arg0)
                expr.methodName in setOf("requireNoNulls", "constrainOnce", "orEmpty") && expr.args.isEmpty() -> return seqStr
                expr.methodName == "ifEmpty" && arg0 != null -> return emitSequenceIfEmptyExpr(expr.obj, arg0)
                expr.methodName == "first" && expr.args.isEmpty() -> return "__nilDecode((($seqStr)())())"
                expr.methodName == "firstOrNull" && expr.args.isEmpty() -> return "__nilDecode((($seqStr)())())"
                expr.methodName in setOf("find", "first", "firstOrNull") && arg0 != null ->
                    return emitSequenceTerminalExpr { emitSequenceFindStatements(it, seqStr, arg0) }
                expr.methodName in setOf("toList", "toMutableList") && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceToListStatements(it, seqStr) }
                expr.methodName == "toSet" && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceToSetStatements(it, seqStr) }
                expr.methodName == "count" && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceCountStatements(it, seqStr) }
                expr.methodName == "count" && arg0 != null -> return emitSequenceTerminalExpr { r ->
                    line("local $r = 0")
                    line("local __fn = ${callbackStrFor(arg0, expr.obj)}")
                    line("for __v in ($seqStr)() do if __fn(__nilDecode(__v)) then $r = $r + 1 end end")
                }
                expr.methodName == "sum" && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceSumStatements(it, seqStr) }
                expr.methodName in setOf("any", "all", "none") && arg0 != null ->
                    return emitSequenceTerminalExpr { emitSequenceAnyAllNoneStatements(it, seqStr, arg0, expr.methodName) }
                expr.methodName == "fold" && expr.args.size == 2 ->
                    return emitSequenceTerminalExpr { emitSequenceFoldStatements(it, seqStr, expr.args[0].toExpression(), expr.args[1].toExpression()) }
                expr.methodName == "forEach" && arg0 != null -> {
                    emitCollectionIIFE(null) { emitSequenceForEachStatement(seqStr, arg0); true }?.let { return it }
                }
                else -> {}
            }
        }
        if ((collClass == "List" || collClass == "Set") && expr.args.isEmpty() && expr.methodName in setOf("random", "randomOrNull") && !isModuleCall(expr.obj)) {
            val items = if (collClass == "Set") "${emitExpression(expr.obj)}.list" else emitExpression(expr.obj)
            if (collClass == "List") useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            val pick = if (collClass == "List") "__nilDecode(t[math.random(#t)])" else "t[math.random(#t)]"
            return if (expr.methodName == "random") "(function(t) return $pick end)($items)"
            else "((function(t) if #t > 0 then return $pick end end)($items))"
        }
        if (collClass == "List") {
            val receiverStr = emitExpression(expr.obj)
            if (expr.methodName == "count" && expr.args.isEmpty()) return "(#$receiverStr)"
            if (expr.methodName == "get" && expr.args.size == 1) {
                useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
                return "__nilDecode($receiverStr[(${emitExpression(expr.args[0].toExpression())}) + 1])"
            }
            if (expr.methodName == "removeAt" && expr.args.size == 1) {
                useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
                return "__nilDecode(table.remove($receiverStr, (${emitExpression(expr.args[0].toExpression())}) + 1))"
            }
            if (expr.methodName == "isEmpty" && expr.args.isEmpty()) return "(#$receiverStr == 0)"
            if (expr.methodName == "isNotEmpty" && expr.args.isEmpty()) return "(#$receiverStr ~= 0)"
            if (expr.methodName in mutationMethodNames.getValue("List")) {
                emitCollectionIIFE(null) { emitInlineMutation(expr) }?.let { return it }
            }
        }
        if (expr.methodName == "forEach" && expr.args.size == 1) {
            val elementClass = (expr.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
            emitCollectionIIFE(null) { emitForEachStatement(expr.obj, expr.args[0].toExpression(), elementClass); true }
                ?.let { return it }
        }
        if (expr.methodName == "forEachIndexed" && expr.args.size == 1 && expr.args[0].toExpression() is LambdaExpr) {
            emitCollectionIIFE(null) { emitForEachIndexedStatement(expr.obj, expr.args[0].toExpression() as LambdaExpr); true }
                ?.let { return it }
        }
        if (expr.methodName in inlineAccumulateMethodNames && receiverInstanceClass(expr.obj) != "Sequence") {
            val elementClass = (expr.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
            val resultVar = nextTemp("__result")
            emitCollectionIIFE(resultVar) { emitInlineAccumulate(expr, resultVar, elementClass) }?.let { return it }
        }
        return "${emitExpression(expr.obj)}.${expr.methodName}(${emitArgList(expr.args)})"
    }

    private fun emitResultMethodCall(expr: MethodCallExpr): String {
        val helper = when (expr.methodName) {
            "onSuccess" -> "__resultOnSuccess"
            "onFailure" -> "__resultOnFailure"
            "getOrNull" -> "__resultGetOrNull"
            else -> error("Result.${expr.methodName} is not supported yet")
        }
        usedRuntimeHelpers += RuntimeHelper.RESULT
        val args = listOf(emitExpression(expr.obj)) + expr.args.map { emitExpression(it.toExpression()) }
        return "$helper(${args.joinToString(", ")})"
    }

    private fun emitScopeFunctionCall(expr: MethodCallExpr): String? {
        if (expr.methodName in setOf("takeIf", "takeUnless") && expr.args.size == 1) {
            usedRuntimeHelpers += RuntimeHelper.TAKE_IF
            val predicate = expr.args.single().toExpression()
            val callback = if (predicate is LambdaExpr) emitLambda(predicate, receiverInstanceClass(expr.obj)) else emitExpression(predicate)
            return "__takeIf(${emitExpression(expr.obj)}, $callback, ${expr.methodName == "takeUnless"})"
        }
        if (expr.methodName == "let" && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) emitLambda(lambdaArg, receiverInstanceClass(expr.obj)) else emitExpression(lambdaArg)
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        if (expr.methodName == "also" && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) {
                emitLambda(lambdaArg, receiverInstanceClass(expr.obj), returnReceiver = true)
            } else {
                emitExpression(lambdaArg)
            }
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        if ((expr.methodName == "run" || expr.methodName == "apply") && expr.args.size == 1) {
            val lambdaArg = expr.args[0].toExpression()
            val fnStr = if (lambdaArg is LambdaExpr) {
                emitLambda(
                    lambdaArg.copy(params = emptyList()),
                    receiverInstanceClass(expr.obj),
                    implicitParamName = receiverParameterName(),
                    returnReceiver = expr.methodName == "apply",
                    receiverScope = true,
                )
            } else {
                emitExpression(lambdaArg)
            }
            return "($fnStr)(${emitExpression(expr.obj)})"
        }
        return null
    }

    private fun emitVariableDecl(decl: VariableDecl) {
        elvisReturn(decl.init)?.let { guard ->
            emitVariableDecl(decl.copy(init = guard.left))
            emitReturnGuard(decl.name, guard.right as ReturnExpr)
            return
        }
        val init = decl.init
        if (tryEmitCollectionConstruction(decl.name, init)) {
            types.env.declare(decl.name, decl.varType ?: TypeName.ANY)
            return
        }
        if (init is MethodCallExpr && init.methodName in inlineAccumulateMethodNames &&
            !receiverOverridesMethod(init.obj, init.methodName) && receiverInstanceClass(init.obj) != "Sequence"
        ) {
            val elementClass = (init.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
            if (emitInlineAccumulate(init, decl.name, elementClass)) {
                types.env.declare(decl.name, decl.varType ?: TypeName.ANY)
                types.declareInstance(decl.name, decl.rawType)
                receiverInstanceClass(init.obj)?.let { collectionMethodResultKind(it, init) }?.let { types.declareInstance(decl.name, it) }
                return
            }
        }
        val type = decl.varType ?: types.inferExprType(decl.init)
        val initStr = emitExpression(decl.init)
        types.env.declare(decl.name, type)
        types.declareInstance(decl.name, decl.rawType)
        instanceClassOfExpr(decl.init)?.let { types.declareInstance(decl.name, it) }
        listElementClassOfExpr(decl.init)?.let { types.declareListElement(decl.name, it) }
        line("local ${decl.name} = $initStr")
    }

    private fun emitDestructuringDecl(stmt: DestructuringDecl) {
        val tempVar = nextTemp("__d")
        line("local $tempVar = ${emitExpression(stmt.init)}")
        val className = receiverInstanceClass(stmt.init)
        val info = className?.let { types.classInfo(it) }
        val fieldOrder = info?.takeIf { !it.native }?.paramOrder
        stmt.names.forEachIndexed { i, name ->
            val valueStr = if (fieldOrder != null) {
                emitPropertyAccess(Identifier(tempVar), fieldOrder.getOrElse(i) { error("Not enough properties on $className to destructure into ${stmt.names.size} names") })
            } else if (i == 0) {
                "$tempVar.first"
            } else {
                "$tempVar.second"
            }
            line("local $name = $valueStr")
            types.env.declare(name, TypeName.ANY)
            if (fieldOrder == null && (stmt.init as? MethodCallExpr)?.methodName in setOf("unzip", "partition")) types.declareInstance(name, "List")
        }
    }

    private fun elvisReturn(expr: Expression): ElvisExpr? = when (expr) {
        is ParenExpr -> elvisReturn(expr.expr)
        is ElvisExpr -> expr.takeIf { it.right is ReturnExpr }
        else -> null
    }

    private fun emitReturnGuard(value: String, exit: ReturnExpr) {
        line("if $value == nil then")
        pushIndent()
        emitStatement(ReturnStatement(exit.value))
        popIndent()
        line("end")
    }

    private fun listElementClassOfExpr(expr: Expression): String? {
        if (expr !is CallExpr || expr.callee != "listOf" || expr.args.isEmpty()) return null
        val elementClasses = expr.args.map { instanceClassOfExpr(it.toExpression()) }.toSet()
        return elementClasses.singleOrNull()
    }

    private fun isDefaultRandom(expr: Expression): Boolean = when (expr) {
        is Identifier -> expr.name == "Random" && types.env.lookup("Random") == null && types.instanceClassOf("Random") == null
        is PropertyAccessExpr -> expr.name == "Default" && isDefaultRandom(expr.obj)
        else -> false
    }

    private fun instanceClassOfExpr(expr: Expression): String? = if (isDefaultRandom(expr)) "Random" else instanceClassOfNonRandomExpr(expr)

    private fun instanceClassOfNonRandomExpr(expr: Expression): String? = when (expr) {
        is ToPairExpr -> "Pair"
        is RangeExpr -> if (isFloatRange(expr)) "FloatRange" else "List"
        is IndexExpr -> (expr.obj as? Identifier)?.takeIf { instanceClassOfExpr(it) == "List" }?.let { types.listElementClassOf(it.name) }
        is StringTemplateLiteral -> "String"
        is Identifier -> {
            val resolvedName = if (expr.name == "self") types.currentReceiver() ?: "self" else expr.name
            types.instanceClassOf(resolvedName) ?: bindings.global(expr.name)
                ?: types.memberReceiver(expr.name)?.let { receiver ->
                    types.instanceClassOf(receiver)?.let { types.classInfo(it)?.propertyTypes?.get(expr.name) }
                }
        }
        is PropertyAccessExpr -> expressionPath(expr)?.let { objectMemberClasses[it] ?: bindings.global(it) }
            ?: receiverInstanceClass(expr.obj)?.let { receiver ->
                if (receiver == "Map" && expr.name in setOf("keys", "values", "entries")) "List"
                else if (types.classInfo(receiver)?.native == true) bindings.property(receiver, expr.name)
                else types.classInfo(receiver)?.propertyTypes?.get(expr.name)
            }
        is ParenExpr -> instanceClassOfExpr(expr.expr)
        is NotNullAssertExpr -> instanceClassOfExpr(expr.expr)
        is CallExpr -> operationRegistry.resolveTopLevel(expr.callee, OperationCall(null, expr.args), this)?.resultKind(null)
            ?: if (expr.callee in setOf("sequenceOf", "generateSequence", "emptySequence", "sequence")) "Sequence"
            else if (expr.callee == "runCatching" && types.memberReceiver(expr.callee) == null) RESULT_CLASS
            else if (expr.callee == "run" && expr.args.size == 1 && types.memberReceiver(expr.callee) == null)
                scopeResultClass(expr.args.single().toExpression(), null, noParams = true)
            else if (expr.callee == "with" && expr.args.size == 2 && types.memberReceiver(expr.callee) == null)
                scopeResultClass(expr.args[1].toExpression(), receiverInstanceClass(expr.args[0].toExpression()), receiverStyle = true)
            else if (types.classInfo(expr.callee) != null) expr.callee
            else types.memberReceiver(expr.callee)?.let { receiver ->
                types.instanceClassOf(receiver)?.let { types.methodReturnClass(it, expr.callee) }
            } ?: types.functionReturnClass(expr.callee)
        is MethodCallExpr -> if (asRange(expr) != null) "List"
        else if (expr.methodName == "copy" && receiverInstanceClass(expr.obj)?.let { bindings.isDataRecord(it) } == true) {
            receiverInstanceClass(expr.obj)
        } else if (expr.methodName in setOf("also", "apply", "takeIf", "takeUnless") && !hasDeclaredMethod(expr)) {
            receiverInstanceClass(expr.obj)
        } else if (expr.methodName in setOf("let", "run") && expr.args.size == 1 && !hasDeclaredMethod(expr)) {
            scopeResultClass(expr.args.single().toExpression(), receiverInstanceClass(expr.obj), receiverStyle = expr.methodName == "run")
        } else {
            if (expr.methodName == "new" && expressionPath(expr.obj)?.let { nativeFactory(it) } != null) {
                bindings.type(expressionPath(expr.obj)!!)
            } else receiverInstanceClass(expr.obj)?.let { receiverClass ->
                if (receiverClass == "Grouping") "Map"
                else if (receiverClass == "Random" || receiverClass == "StringBuilder") types.methodReturnClass(receiverClass, expr.methodName)
                else if (receiverClass == RESULT_CLASS && expr.methodName in setOf("onSuccess", "onFailure")) RESULT_CLASS
                else if (receiverClass in setOf("List", "Set", "Map", "Sequence")) collectionMethodResultKind(receiverClass, expr)
                else if (types.classInfo(receiverClass)?.native == true) bindings.method(receiverClass, expr.methodName)?.second
                else types.methodReturnClass(receiverClass, expr.methodName)
            }
        }
        is SafeChainExpr -> receiverInstanceClass(expr.steps.fold(expr.base) { current, step ->
            when (step) {
                is ChainProp -> PropertyAccessExpr(current, step.name)
                is ChainCall -> MethodCallExpr(current, step.name, step.args)
            }
        })
        is ElvisExpr -> receiverInstanceClass(expr.left) ?: receiverInstanceClass(expr.right)
        is BinaryExpr -> if (expr.op == "+" || expr.op == "-") {
            receiverInstanceClass(expr.left)?.takeIf { it == "List" || it == "Set" || it == "Sequence" }
        } else null
        is IfExpr -> receiverInstanceClass(expr.thenExpr)?.takeIf { it == receiverInstanceClass(expr.elseExpr) }
        else -> null
    }

    override fun receiverInstanceClass(expr: Expression): String? =
        instanceClassOfExpr(expr)

    private fun hasDeclaredMethod(expr: MethodCallExpr): Boolean =
        receiverInstanceClass(expr.obj)?.let { types.classInfo(it)?.methodReturnTypes?.containsKey(expr.methodName) } == true

    private fun expressionPath(expr: Expression): String? = when (expr) {
        is Identifier -> expr.name
        is PropertyAccessExpr -> expressionPath(expr.obj)?.let { "$it.${expr.name}" }
        else -> null
    }

    private fun nativeFactory(path: String): String? =
        bindings.factory(path)?.takeIf { types.classInfo(path)?.native != false && types.env.lookup(path) == null }

    private fun receiverParameterName(): String =
        if (types.currentReceiver() == null) "self" else nextTemp("__receiver")

    private fun scopeResultClass(block: Expression, receiverClass: String?, receiverStyle: Boolean = false, noParams: Boolean = false): String? {
        if (block !is LambdaExpr) return (block as? FunctionRef)?.let { types.functionReturnClass(it.name) }
        types.pushScope()
        if (receiverStyle) {
            types.declareReceiver("__scopeReceiver", receiverClass ?: "")
        } else if (!noParams) {
            val parameter = block.params.firstOrNull() ?: "it"
            types.env.declare(parameter, TypeName.ANY)
            receiverClass?.let { types.declareInstance(parameter, it) }
        }
        val result = blockResultClass(block.body)
        types.popScope()
        return result
    }

    private fun blockResultClass(block: Block): String? {
        for (statement in block.body.dropLast(1)) {
            if (statement is VariableDecl) {
                val inferred = receiverInstanceClass(statement.init)
                types.env.declare(statement.name, statement.varType ?: types.inferExprType(statement.init))
                types.declareInstance(statement.name, statement.rawType)
                inferred?.let { types.declareInstance(statement.name, it) }
            }
        }
        return when (val last = block.body.lastOrNull()) {
            is ExpressionStatement -> receiverInstanceClass(last.expr)
            is ReturnStatement -> last.expr?.let { receiverInstanceClass(it) }
            is IfStatement -> {
                fun branch(block: Block): String? {
                    types.pushScope()
                    val result = blockResultClass(block)
                    types.popScope()
                    return result
                }
                val left = branch(last.thenBranch)
                left?.takeIf { last.elseBranch != null && it == branch(last.elseBranch) }
            }
            else -> null
        }
    }

    private fun emitAssignment(stmt: Assignment) {
        val target = stmt.target
        if (stmt.op == "=" && target is IndexTarget && receiverInstanceClass(target.obj) == "Map") {
            emitMapPut(emitExpression(target.obj), emitExpression(target.index), emitExpression(stmt.expr), target.index)
            return
        }
        if (stmt.op == "=" && target is IndexTarget && receiverInstanceClass(target.obj) == "List") {
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            line("${emitAssignTarget(target)} = __nilEncode(${emitExpression(stmt.expr)})")
            return
        }
        val rhs = stmt.expr
        if (stmt.op == "=" && stmt.target is NameTarget && rhs is MethodCallExpr &&
            rhs.methodName in inlineAccumulateMethodNames && !receiverOverridesMethod(rhs.obj, rhs.methodName) &&
            receiverInstanceClass(rhs.obj) != "Sequence"
        ) {
            val elementClass = (rhs.obj as? Identifier)?.let { types.listElementClassOf(it.name) }
            val temp = nextTemp("__tmp")
            if (emitInlineAccumulate(rhs, temp, elementClass)) {
                line("${stmt.target.name} = $temp")
                return
            }
        }
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
            line("$targetStr = (math.modf($targetStr / $rhsStr))")
        } else if (binOp == "%") {
            line("$targetStr = math.fmod($targetStr, $rhsStr)")
        } else if (binOp == "+" && (targetType == TypeName.STRING || types.inferExprType(stmt.expr) == TypeName.STRING)) {
            line("$targetStr = ${concatOperand(Identifier(""), targetStr, forceString = targetType == TypeName.STRING)} .. ${concatOperand(stmt.expr, rhsStr)}")
        } else {
            line("$targetStr = $targetStr $binOp ($rhsStr)")
        }
    }

    private fun concatOperand(expr: Expression, str: String, forceString: Boolean = false): String =
        if (forceString || types.inferExprType(expr) == TypeName.STRING) str else formatForToString(expr, str)

    private fun emitAssignTarget(target: AssignTarget): String = when (target) {
        is NameTarget -> emitIdentifier(target.name)
        is PropertyTarget -> emitPropertyAccess(target.obj, target.name)
        is IndexTarget -> {
            val indexStr = emitExpression(target.index)
            val receiverStr = emitExpression(target.obj)
            when {
                receiverInstanceClass(target.obj) == "Map" -> "$receiverStr.map[${canonicalKeyStr(target.index, indexStr)}]"
                types.inferExprType(target.index) == TypeName.STRING -> "$receiverStr[$indexStr]"
                else -> "$receiverStr[($indexStr) + 1]"
            }
        }
    }

    override fun emitExpression(expr: Expression): String = when (expr) {
        is IntLiteral -> expr.value.toString()
        is DoubleLiteral -> formatDouble(expr.value)
        is CharLiteral -> quoteLuaString(expr.value.toString(), style.quoteStyle)
        is BooleanLiteral -> expr.value.toString()
        NullLiteral -> "nil"
        is Identifier -> emitIdentifier(expr.name)
        is ParenExpr -> "(${emitExpression(expr.expr)})"
        is UnaryExpr -> if (expr.op == "!") "(not ${emitExpression(expr.expr)})" else "(-${emitExpression(expr.expr)})"
        is StringTemplateLiteral -> emitTemplate(expr)
        is BinaryExpr -> emitBinary(expr)
        is CallExpr -> emitCall(expr)
        is PropertyAccessExpr -> receiverInstanceClass(expr.obj).let { cls ->
            if (isLengthProperty(expr.name) && (cls == null || cls in setOf("String", "List", "Set", "Map", "StringBuilder"))) lengthAccess(emitExpression(expr.obj), cls)
            else emitPropertyAccess(expr.obj, expr.name)
        }
        is MethodCallExpr -> emitMethodCallTarget(expr)
        is FunctionRef -> if (expr.name == "println") "print" else expr.name
        is IfExpr -> "(${emitExpression(expr.cond)} and ${emitExpression(expr.thenExpr)} or ${emitExpression(expr.elseExpr)})"
        is WhenExpr -> WhenEmitter.emitWhenExpr(expr, this)
        is ElvisExpr -> emitElvis(expr)
        is ReturnExpr -> error("An Elvis return must be a local variable initializer or a guard statement")
        is SafeChainExpr -> emitSafeChain(expr)
        is NotNullAssertExpr -> emitNotNullAssert(expr)
        is LambdaExpr -> emitLambda(expr)
        is IndexExpr -> emitIndex(expr)
        is ToPairExpr -> "{first = ${emitExpression(expr.first)}, second = ${emitExpression(expr.second)}}"
        is RangeExpr -> emitRangeExpr(expr)
        is IsExpr -> {
            val check = "(${instancePredicate(expr.typeName)})(${emitExpression(expr.expr)})"
            if (expr.negated) "(not $check)" else check
        }
    }

    private fun emitPropertyAccess(receiver: Expression, name: String): String {
        if (name in setOf("first", "last") && receiverInstanceClass(receiver) == "List") {
            return inlineOnce(listOf(receiver), listOf(emitExpression(receiver))) { (t) -> if (name == "first") "$t[1]" else "$t[#$t]" }
        }
        if (name == "code" && receiverInstanceClass(receiver) == null) return "string.byte(${emitExpression(receiver)})"
        if (name == "absoluteValue" && receiverInstanceClass(receiver) == null) return "math.abs(${emitExpression(receiver)})"
        if (name == "ulp" && receiverInstanceClass(receiver) == null) {
            useRuntimeHelper(RuntimeHelper.MATH_FLOAT)
            return "__ulp(${emitExpression(receiver)})"
        }
        if (isDefaultRandom(PropertyAccessExpr(receiver, name))) return emitExpression(receiver)
        if (receiver is Identifier && receiver.name == "Int" && types.env.lookup("Int") == null) {
            when (name) {
                "MAX_VALUE" -> return "2147483647"
                "MIN_VALUE" -> return "(-2147483648)"
            }
        }
        if (receiverInstanceClass(receiver) == "Map" && name in setOf("keys", "values", "entries")) {
            val receiverStr = emitExpression(receiver)
            return when (name) {
                "keys" -> "$receiverStr.keys"
                "values" -> "(function() local __r = $receiverStr local __v = {} for _, __k in ipairs(__r.keys) do table.insert(__v, __r.map[__k]) end return __v end)()"
                else -> "(function() local __r = $receiverStr local __e = {} for _, __k in ipairs(__r.keys) do table.insert(__e, {key = __k, value = __r.map[__k]}) end return __e end)()"
            }
        }
        expressionPath(PropertyAccessExpr(receiver, name))?.let { path ->
            bindings.hostPath(path)?.let { return it }
            if (types.classInfo(path)?.native != false) bindings.luaName(path)?.let { return it }
        }
        val target = emitExpression(receiver)
        val hostName = receiverInstanceClass(receiver)?.takeIf { types.classInfo(it)?.native == true }
            ?.let { bindings.propertyName(it, name) } ?: name
        val reserved = setOf("and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while")
        return if (hostName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && hostName !in reserved) "$target.$hostName"
        else "$target[${quoteLuaString(hostName, style.quoteStyle)}]"
    }

    private fun emitIdentifier(name: String): String {
        if (name == "self") return types.currentReceiver() ?: name
        objectMemberPrefix(name, objectVars)?.let { return "$it.$name" }
        if (isDefaultRandom(Identifier(name))) {
            useRuntimeHelper(RuntimeHelper.RANDOM)
            return "__Random.Default"
        }
        if ((name == "PI" || name == "E") && types.env.lookup(name) == null && types.classInfo(name) == null) {
            return if (name == "PI") "math.pi" else "math.exp(1)"
        }
        types.memberReceiver(name)?.let { receiver ->
            val className = types.instanceClassOf(receiver)
            val hostName = className?.takeIf { types.classInfo(it)?.native == true }?.let { bindings.propertyName(it, name) } ?: name
            return if (isLengthProperty(name) && className in setOf("String", "List", "Set", "Map")) lengthAccess(receiver, className) else "$receiver.$hostName"
        }
        return if (types.env.lookup(name) == null && types.instanceClassOf(name) == null && types.classInfo(name)?.native != false)
            bindings.luaName(name) ?: name else name
    }

    private fun emitIndex(expr: IndexExpr): String {
        val indexStr = emitExpression(expr.index)
        val receiverStr = emitExpression(expr.obj)
        return when {
            receiverInstanceClass(expr.obj) == "Map" -> "$receiverStr.map[${canonicalKeyStr(expr.index, indexStr)}]"
            types.inferExprType(expr.index) == TypeName.STRING -> "$receiverStr[$indexStr]"
            receiverInstanceClass(expr.obj) == "List" -> {
                useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
                "__nilDecode($receiverStr[($indexStr) + 1])"
            }
            else -> "$receiverStr[($indexStr) + 1]"
        }
    }

    private fun emitLambda(
        expr: LambdaExpr,
        elementClass: String? = null,
        implicitParamName: String = "it",
        returnReceiver: Boolean = false,
        receiverScope: Boolean = false,
        noParams: Boolean = false,
    ): String {
        val paramNames = if (noParams) emptyList() else expr.params.ifEmpty { listOf(implicitParamName) }
        types.pushScope()
        paramNames.forEach { types.env.declare(it, TypeName.ANY) }
        if (elementClass != null) types.declareInstance(paramNames[0], elementClass)
        if (receiverScope) types.declareReceiver(paramNames[0], elementClass ?: "")

        val savedOut = out
        val savedIndent = indentLevel
        val savedInsideTry = insideTryClosure
        insideTryClosure = false
        out = StringBuilder()
        indentLevel = savedIndent + 1
        val stmts = expr.body.body
        stmts.forEachIndexed { i, stmt ->
            if (!returnReceiver && i == stmts.lastIndex) emitLambdaResult(stmt) { "return $it" }
            else emitStatement(stmt)
        }
        if (returnReceiver) line("return ${paramNames[0]}")
        val bodyLua = out.toString()
        out = savedOut
        indentLevel = savedIndent
        insideTryClosure = savedInsideTry

        types.popScope()
        return "function(${paramNames.joinToString(", ")})\n${bodyLua}${indentUnit.repeat(savedIndent)}end"
    }

    override fun emitLambdaResult(statement: Statement, sink: (String) -> String) {
        when {
            statement is ExpressionStatement -> line(sink(emitExpression(statement.expr)))
            statement is IfStatement && statement.elseBranch != null -> {
                fun branch(block: Block) {
                    pushIndent()
                    types.pushScope()
                    block.body.forEachIndexed { index, nested ->
                        if (index == block.body.lastIndex) emitLambdaResult(nested, sink) else emitStatement(nested)
                    }
                    types.popScope()
                    popIndent()
                }
                line("if ${emitExpression(statement.cond)} then")
                branch(statement.thenBranch)
                line("else")
                branch(statement.elseBranch)
                line("end")
            }
            else -> emitStatement(statement)
        }
    }

    override fun isModuleCall(expr: Expression): Boolean = expr is Identifier && expr.name in importedModules

    override fun isRepeatableExpr(expr: Expression): Boolean = when (expr) {
        is Identifier -> true
        is PropertyAccessExpr -> isRepeatableExpr(expr.obj)
        is ParenExpr -> isRepeatableExpr(expr.expr)
        else -> false
    }

    override fun hoistIfNeeded(expr: Expression): String {
        val str = emitExpression(expr)
        if (isRepeatableExpr(expr)) return str
        val tempVar = nextTemp("__recv")
        line("local $tempVar = $str")
        return tempVar
    }

    private fun receiverOverridesMethod(obj: Expression, methodName: String): Boolean {
        val className = receiverInstanceClass(obj) ?: return false
        val info = types.classInfo(className) ?: return false
        return if (info.native) bindings.method(className, methodName) != null else methodName in info.methodNames
    }

    private fun emitForEachStatement(obj: Expression, callback: Expression, elementClass: String?) {
        val cls = receiverInstanceClass(obj) ?: "List"
        val call = OperationCall(obj, listOf(PositionalArg(callback)), elementClass)
        operationRegistry.resolve(cls, "forEach", call, this)?.lower(call, null, this)
    }



    private fun emitInlineMutation(expr: MethodCallExpr): Boolean {
        val cls = receiverInstanceClass(expr.obj) ?: return false
        val call = OperationCall(expr.obj, expr.args)
        val op = operationRegistry.resolve(cls, expr.methodName, call, this)?.takeIf { !it.needsResultVar() } ?: return false
        return op.lower(call, null, this)
    }

    private fun tryEmitCollectionConstruction(name: String, init: Expression): Boolean {
        if (init !is CallExpr) return false
        val call = OperationCall(null, init.args)
        val op = operationRegistry.resolveTopLevel(init.callee, call, this) ?: return false
        op.lower(call, name, this)
        val kind = op.resultKind(null)!!
        types.declareInstance(name, kind)
        if (kind == "List") listElementClassOfExpr(init)?.let { types.declareListElement(name, it) }
        return true
    }

    private fun emitContains(collection: Expression, collStr: String, valueStr: String, valueExpr: Expression? = null): String = when (receiverInstanceClass(collection)) {
        "Set" -> "($collStr.has[${valueExpr?.let { canonicalKeyStr(it, valueStr) } ?: valueStr}] ~= nil)"
        "Map" -> "($collStr.map[${valueExpr?.let { canonicalKeyStr(it, valueStr) } ?: valueStr}] ~= nil)"
        "String" -> "(string.find($collStr, $valueStr, 1, true) ~= nil)"
        "FloatRange" -> "(function(v, r) return v >= r.start and v <= r.endInclusive end)($valueStr, $collStr)"
        "Sequence" -> "(function(x) for __v in ($collStr)() do if __nilDecode(__v) == x then return true end end return false end)($valueStr)"
        "List" -> {
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            val encodedValue = "__nilEncode($valueStr)"
            val eq = valueExpr?.let { structuralEq(it, "__v", encodedValue) } ?: "(__v == $encodedValue)"
            "(function() for _, __v in ipairs($collStr) do if $eq then return true end end return false end)()"
        }
        else -> {
            val eq = valueExpr?.let { structuralEq(it, "__v", valueStr) } ?: "(__v == $valueStr)"
            "(function() for _, __v in ipairs($collStr) do if $eq then return true end end return false end)()"
        }
    }

    private fun emitDataClassCopyExpr(className: String, info: ClassInfo, objExpr: Expression, args: List<CallArg>): String {
        val namedArgs = args.filterIsInstance<NamedArg>().associateBy { it.name }
        if (isRepeatableExpr(objExpr)) {
            val fieldExprs = info.paramOrder.map { field -> namedArgs[field]?.let { emitExpression(it.expr) } ?: emitPropertyAccess(objExpr, field) }
            return "$className.new(${fieldExprs.joinToString(", ")})"
        }
        val srcVar = nextTemp("__src")
        val fieldExprs = info.paramOrder.map { field -> namedArgs[field]?.let { emitExpression(it.expr) } ?: "$srcVar.$field" }
        return "(function() local $srcVar = ${emitExpression(objExpr)} return $className.new(${fieldExprs.joinToString(", ")}) end)()"
    }

    private fun emitHashCodeExpr(obj: Expression): String {
        val formatted = formatForToString(obj, emitExpression(obj))
        return "(function() local __s = $formatted local __h = 0 for __i = 1, #__s do __h = (__h * 31 + string.byte(__s, __i)) % 2147483647 end return __h end)()"
    }

    private fun emitCollectionEquals(leftExpr: Expression, leftStr: String, rightStr: String): String = when (receiverInstanceClass(leftExpr)) {
        "List" -> "(function() local __a, __b = $leftStr, $rightStr; if #__a ~= #__b then return false end for __i = 1, #__a do if tostring(__a[__i]) ~= tostring(__b[__i]) then return false end end return true end)()"
        "Set" -> "(function() local __a, __b = $leftStr, $rightStr; if #__a.list ~= #__b.list then return false end for _, __v in ipairs(__a.list) do if not __b.has[__v] then return false end end return true end)()"
        "Map" -> "(function() local __a, __b = $leftStr, $rightStr; if #__a.keys ~= #__b.keys then return false end for _, __k in ipairs(__a.keys) do if __a.map[__k] ~= __b.map[__k] then return false end end return true end)()"
        else -> "($leftStr == $rightStr)"
    }

    private fun arrayFormOf(expr: Expression, str: String): String = if (receiverInstanceClass(expr) == "Set") "$str.list" else str

    private fun emitListPlusExpr(leftStr: String, rightExpr: Expression, rightStr: String): String {
        val rightKind = receiverInstanceClass(rightExpr)
        return if (rightKind == "List" || rightKind == "Set") {
            val rightArr = arrayFormOf(rightExpr, rightStr)
            "(function() local __c = {} for _, __v in ipairs($leftStr) do table.insert(__c, __v) end for _, __v in ipairs($rightArr) do table.insert(__c, __v) end return __c end)()"
        } else {
            "(function() local __c = {} for _, __v in ipairs($leftStr) do table.insert(__c, __v) end table.insert(__c, $rightStr) return __c end)()"
        }
    }

    private fun emitListMinusExpr(leftStr: String, rightExpr: Expression, rightStr: String): String {
        val rightKind = receiverInstanceClass(rightExpr)
        return if (rightKind == "List" || rightKind == "Set") {
            val rightArr = arrayFormOf(rightExpr, rightStr)
            "(function() local __rm = {} for _, __v in ipairs($rightArr) do __rm[__v] = true end local __c = {} for _, __v in ipairs($leftStr) do if not __rm[__v] then table.insert(__c, __v) end end return __c end)()"
        } else {
            "(function() local __c = {} local __done = false for _, __v in ipairs($leftStr) do if not __done and __v == $rightStr then __done = true else table.insert(__c, __v) end end return __c end)()"
        }
    }

    private fun emitSetPlusMinusExpr(leftStr: String, rightExpr: Expression, rightStr: String, removing: Boolean): String {
        val rightKind = receiverInstanceClass(rightExpr)
        val rightArr = (rightKind == "List" || rightKind == "Set").let { if (it) arrayFormOf(rightExpr, rightStr) else null }
        return when {
            !removing && rightArr != null ->
                "(function() local __c = {list = {}, has = {}} for _, __v in ipairs($leftStr.list) do if not __c.has[__v] then table.insert(__c.list, __v); __c.has[__v] = true end end for _, __v in ipairs($rightArr) do if not __c.has[__v] then table.insert(__c.list, __v); __c.has[__v] = true end end return __c end)()"
            !removing ->
                "(function() local __c = {list = {}, has = {}} for _, __v in ipairs($leftStr.list) do if not __c.has[__v] then table.insert(__c.list, __v); __c.has[__v] = true end end if not __c.has[$rightStr] then table.insert(__c.list, $rightStr); __c.has[$rightStr] = true end return __c end)()"
            rightArr != null ->
                "(function() local __rm = {} for _, __v in ipairs($rightArr) do __rm[__v] = true end local __c = {list = {}, has = {}} for _, __v in ipairs($leftStr.list) do if not __rm[__v] then table.insert(__c.list, __v); __c.has[__v] = true end end return __c end)()"
            else ->
                "(function() local __c = {list = {}, has = {}} for _, __v in ipairs($leftStr.list) do if __v ~= $rightStr then table.insert(__c.list, __v); __c.has[__v] = true end end return __c end)()"
        }
    }

    private fun collectComparatorChain(expr: Expression): List<Pair<Expression, Boolean>>? = when {
        expr is CallExpr && expr.callee == "compareBy" && expr.args.size == 1 -> listOf(expr.args[0].toExpression() to false)
        expr is CallExpr && expr.callee == "compareByDescending" && expr.args.size == 1 -> listOf(expr.args[0].toExpression() to true)
        expr is MethodCallExpr && expr.methodName == "thenBy" && expr.args.size == 1 ->
            collectComparatorChain(expr.obj)?.plus(expr.args[0].toExpression() to false)
        expr is MethodCallExpr && expr.methodName == "thenByDescending" && expr.args.size == 1 ->
            collectComparatorChain(expr.obj)?.plus(expr.args[0].toExpression() to true)
        else -> null
    }

    private fun emitComparatorChainExpr(chain: List<Pair<Expression, Boolean>>): String {
        val sb = StringBuilder("(function(__a, __b)\n")
        for ((selector, descending) in chain) {
            val selectorStr = if (selector is LambdaExpr) emitLambda(selector) else emitExpression(selector)
            val lt = if (descending) "1" else "-1"
            val gt = if (descending) "-1" else "1"
            sb.append("${indentUnit}do local __fn = $selectorStr; local __ka, __kb = __fn(__a), __fn(__b); if __ka < __kb then return $lt elseif __ka > __kb then return $gt end end\n")
        }
        sb.append("${indentUnit}return 0\n")
        sb.append("end)")
        return sb.toString()
    }

    private fun emitSequenceExpr(setup: () -> Unit, produce: () -> Unit): String {
        useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        val savedOut = out
        val savedIndent = indentLevel
        out = StringBuilder()
        indentLevel = savedIndent + 1
        setup()
        line("return function()")
        pushIndent()
        produce()
        popIndent()
        line("end")
        val bodyLua = out.toString()
        out = savedOut
        indentLevel = savedIndent
        return "function()\n${bodyLua}${indentUnit.repeat(savedIndent)}end"
    }

    private fun emitListToSequenceExpr(arrExpr: Expression): String = emitSequenceExpr(
        setup = { line("local __arr = ${iterationArrayOf(arrExpr)}"); line("local __i = 0") },
        produce = { line("__i = __i + 1"); line("return __arr[__i]") },
    )

    private fun emitSequenceOfExpr(args: List<CallArg>): String = emitSequenceExpr(
        setup = {
            line("local __arr = {${args.joinToString(", ") { "__nilEncode(${emitExpression(it.toExpression())})" }}}")
            line("local __i = 0")
        },
        produce = { line("__i = __i + 1"); line("return __arr[__i]") },
    )

    private fun emitGenerateSequenceExpr(seed: Expression, nextFn: Expression): String = emitSequenceExpr(
        setup = {
            line("local __cur = ${emitExpression(seed)}")
            line("local __fn = ${if (nextFn is LambdaExpr) emitLambda(nextFn) else emitExpression(nextFn)}")
        },
        produce = {
            line("local __v = __cur")
            line("if __v ~= nil then __cur = __fn(__v) end")
            line("return __v")
        },
    )

    private fun emitSequenceUpstream(upstream: Expression) {
        line("local __it = (${emitExpression(upstream)})()")
    }

    private fun emitSequenceMapExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStrFor(callback, upstream)}")
        },
        produce = {
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("return __nilEncode(__fn(__nilDecode(__v)))")
        },
    )

    private fun emitSequenceFilterExpr(upstream: Expression, callback: Expression?, negate: Boolean = false, predicateLua: String? = null): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${predicateLua ?: callbackStrFor(callback!!, upstream)}")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line(if (negate) "if not __fn(__nilDecode(__v)) then return __v end" else "if __fn(__nilDecode(__v)) then return __v end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceMapNotNullExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStrFor(callback, upstream)}")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("local __r = __fn(__nilDecode(__v))")
            line("if __r ~= nil then return __r end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceFilterNotNullExpr(upstream: Expression): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream) },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("if __v ~= __NULL then return __v end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceOnEachExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStrFor(callback, upstream)}")
        },
        produce = {
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("__fn(__nilDecode(__v))")
            line("return __v")
        },
    )

    private fun emitSequenceTakeExpr(upstream: Expression, countStr: String): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream); line("local __n = $countStr"); line("local __count = 0") },
        produce = {
            line("if __count >= __n then return nil end")
            line("__count = __count + 1")
            line("return __it()")
        },
    )

    private fun emitSequenceDropExpr(upstream: Expression, countStr: String): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream); line("local __n = $countStr"); line("local __skipped = false") },
        produce = {
            line("if not __skipped then")
            pushIndent()
            line("for __i = 1, __n do __it() end")
            line("__skipped = true")
            popIndent()
            line("end")
            line("return __it()")
        },
    )

    private fun emitSequenceTakeWhileExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStrFor(callback, upstream)}")
            line("local __done = false")
        },
        produce = {
            line("if __done then return nil end")
            line("local __v = __it()")
            line("if __v == nil or not __fn(__nilDecode(__v)) then __done = true; return nil end")
            line("return __v")
        },
    )

    private fun emitSequenceDropWhileExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStrFor(callback, upstream)}")
            line("local __dropping = true")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("if not __dropping then return __v end")
            line("if not __fn(__nilDecode(__v)) then __dropping = false; return __v end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceDistinctExpr(upstream: Expression): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream); line("local __seen = {}") },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("local __k = __v")
            line("if type(__k) == \"table\" then local __m = getmetatable(__k); if __m and __m.__data then __k = tostring(__k) end end")
            line("if not __seen[__k] then __seen[__k] = true; return __v end")
            popIndent()
            line("end")
        },
    )

    private fun callbackStr(callback: Expression, elementClass: String? = null): String =
        if (callback is LambdaExpr) emitLambda(callback, elementClass) else emitExpression(callback)

    private fun callbackStrFor(callback: Expression, upstream: Expression): String = callbackStr(callback, sequenceElementClass(upstream))

    /** The class every element of a Sequence is known to be, so a lambda over it can call that class's methods (`it.sum()` on windows). */
    private fun sequenceElementClass(seq: Expression): String? = when {
        seq is ParenExpr -> sequenceElementClass(seq.expr)
        seq is MethodCallExpr && seq.methodName in setOf("windowed", "chunked") && seq.args.lastOrNull()?.toExpression() !is LambdaExpr -> "List"
        seq is MethodCallExpr && seq.methodName in setOf("filter", "filterNot", "take", "drop", "takeWhile", "dropWhile", "distinct", "distinctBy", "onEach",
            "onEachIndexed", "filterIndexed", "requireNoNulls", "constrainOnce", "orEmpty", "plus", "minus", "plusElement", "minusElement") -> sequenceElementClass(seq.obj)
        seq is CallExpr && seq.callee == "sequenceOf" && seq.args.isNotEmpty() &&
            seq.args.all { receiverInstanceClass(it.toExpression()) == "List" } -> "List"
        else -> null
    }

    private fun emitSequenceDistinctByExpr(upstream: Expression, selector: Expression): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream); line("local __fn = ${callbackStrFor(selector, upstream)}"); line("local __seen = {}") },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("local __k = __nilEncode(__fn(__nilDecode(__v)))")
            line("if type(__k) == \"table\" then local __m = getmetatable(__k); if __m and __m.__data then __k = tostring(__k) end end")
            line("if not __seen[__k] then __seen[__k] = true; return __v end")
            popIndent()
            line("end")
        },
    )

    /** mapIndexed / mapIndexedNotNull / filterIndexed / onEachIndexed (the callback gets the index first) and withIndex. */
    private fun emitSequenceIndexedExpr(kind: String, upstream: Expression, callback: Expression?): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            if (callback != null) line("local __fn = ${callbackStr(callback)}")
            line("local __idx = -1")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("__idx = __idx + 1")
            when (kind) {
                "mapIndexed" -> line("return __nilEncode(__fn(__idx, __nilDecode(__v)))")
                "mapIndexedNotNull" -> line("local __r = __fn(__idx, __nilDecode(__v)); if __r ~= nil then return __r end")
                "filterIndexed" -> line("if __fn(__idx, __nilDecode(__v)) then return __v end")
                "onEachIndexed" -> line("__fn(__idx, __nilDecode(__v)); return __v")
                else -> line("return {index = __idx, value = __nilDecode(__v), first = __idx, second = __nilDecode(__v)}")
            }
            popIndent()
            line("end")
        },
    )

    /** flatMap / flatMapIndexed / flatten: the inner value may be a List, a Set or a Sequence. */
    private fun emitSequenceFlatMapExpr(upstream: Expression, callback: Expression?, indexed: Boolean): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            if (callback != null) line("local __fn = ${callbackStrFor(callback, upstream)}")
            line("local __inner = nil")
            if (indexed) line("local __idx = -1")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("if __inner then")
            pushIndent()
            line("local __x = __inner()")
            line("if __x ~= nil then return __x end")
            line("__inner = nil")
            popIndent()
            line("end")
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            if (indexed) line("__idx = __idx + 1")
            line("local __r = " + when {
                callback == null -> "__v"
                indexed -> "__fn(__idx, __nilDecode(__v))"
                else -> "__fn(__nilDecode(__v))"
            })
            line("if type(__r) == \"function\" then __inner = __r() else local __a = __r.list or __r; local __i = 0; __inner = function() __i = __i + 1; return __a[__i] end end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceZipExpr(upstream: Expression, other: Expression, transform: Expression?): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __it2 = (${emitExpression(other)})()")
            if (transform != null) line("local __fn = ${callbackStr(transform)}")
        },
        produce = {
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("local __w = __it2()")
            line("if __w == nil then return nil end")
            line(if (transform != null) "return __nilEncode(__fn(__nilDecode(__v), __nilDecode(__w)))" else "return {first = __nilDecode(__v), second = __nilDecode(__w)}")
        },
    )

    private fun emitSequenceZipWithNextExpr(upstream: Expression, transform: Expression?): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            if (transform != null) line("local __fn = ${callbackStr(transform)}")
            line("local __prev = __it()")
        },
        produce = {
            line("local __v = __it()")
            line("if __prev == nil or __v == nil then return nil end")
            line("local __p = __prev")
            line("__prev = __v")
            line(if (transform != null) "return __nilEncode(__fn(__nilDecode(__p), __nilDecode(__v)))" else "return {first = __nilDecode(__p), second = __nilDecode(__v)}")
        },
    )

    /** windowed(size, step, partialWindows[, transform]) and chunked(size[, transform]) (= windowed(size, size, true)). */
    private fun emitSequenceWindowedExpr(expr: MethodCallExpr): String {
        val chunked = expr.methodName == "chunked"
        val transform = expr.args.lastOrNull()?.toExpression()?.takeIf { it is LambdaExpr }
        val plain = if (transform != null) expr.args.dropLast(1) else expr.args
        val size = resolveNamedOrPositional(plain, "size", 0)!!
        val step = if (chunked) size else resolveNamedOrPositional(plain, "step", 1)
        val partial = if (chunked) null else resolveNamedOrPositional(plain, "partialWindows", 2)
        return emitSequenceExpr(
            setup = {
                emitSequenceUpstream(expr.obj)
                line("local __size = ${emitExpression(size)}")
                line("local __step = ${step?.let { emitExpression(it) } ?: "1"}")
                line("local __partial = ${if (chunked) "true" else partial?.let { emitExpression(it) } ?: "false"}")
                if (transform != null) line("local __fn = ${callbackStr(transform, "List")}")
                line("local __w = {}")
                line("local __started = false")
                line("local __finished = false")
            },
            produce = {
                line("if __finished then return nil end")
                line("if __started then")
                pushIndent()
                line("local __drop = math.min(__step, #__w)")
                line("for __k = 1, __drop do table.remove(__w, 1) end")
                line("for __k = 1, __step - __drop do __it() end")
                popIndent()
                line("end")
                line("__started = true")
                line("while #__w < __size do local __v = __it(); if __v == nil then break end; __w[#__w + 1] = __v end")
                line("if #__w == 0 then __finished = true; return nil end")
                line("if #__w < __size then __finished = true; if not __partial then return nil end end")
                line("local __out = {}")
                line("for __k = 1, #__w do __out[__k] = __w[__k] end")
                line(if (transform != null) "return __nilEncode(__fn(__out))" else "return __out")
            },
        )
    }

    /** What to append (plus) or remove (minus): a Sequence, a List/Set, or a single element. */
    private fun extraIsCollection(arg: Expression): String? = receiverInstanceClass(arg)?.takeIf { it in setOf("Sequence", "List", "Set") }

    private fun emitSequencePlusExpr(upstream: Expression, arg: Expression, element: Boolean): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            val kind = if (element) null else extraIsCollection(arg)
            when (kind) {
                "Sequence" -> line("local __ex = (${emitExpression(arg)})()")
                null -> {
                    line("local __e = __nilEncode(${emitExpression(arg)})")
                    line("local __d = false")
                    line("local __ex = function() if __d then return nil end __d = true; return __e end")
                }
                else -> {
                    line("local __a = ${iterationArrayOf(arg)}")
                    line("local __i = 0")
                    line("local __ex = function() __i = __i + 1; return __a[__i] end")
                }
            }
            line("local __first = true")
        },
        produce = {
            line("if __first then")
            pushIndent()
            line("local __v = __it()")
            line("if __v ~= nil then return __v end")
            line("__first = false")
            popIndent()
            line("end")
            line("return __ex()")
        },
    )

    private fun emitSequenceMinusExpr(upstream: Expression, arg: Expression, element: Boolean): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            val kind = if (element) null else extraIsCollection(arg)
            if (kind == null) {
                line("local __e = __nilEncode(${emitExpression(arg)})")
                line("local __removed = false")
            } else {
                line("local __ex = {}")
                if (kind == "Sequence") line("for __x in (${emitExpression(arg)})() do __ex[__x] = true end")
                else line("for _, __x in ipairs(${iterationArrayOf(arg)}) do __ex[__x] = true end")
            }
        },
        produce = {
            val kind = if (element) null else extraIsCollection(arg)
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            if (kind == null) {
                line("if not __removed and __v == __e then __removed = true else return __v end")
            } else {
                line("if not __ex[__v] then return __v end")
            }
            popIndent()
            line("end")
        },
    )

    /** runningFold / scan (initial value first) and runningReduce (first element first), with or without the index. */
    private fun emitSequenceRunningExpr(upstream: Expression, fold: Boolean, indexed: Boolean, initial: Expression?, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${callbackStr(callback)}")
            line("local __acc = ${initial?.let { emitExpression(it) } ?: "nil"}")
            if (fold) line("local __started = false")
            line("local __idx = ${if (fold) "-1" else "0"}")
        },
        produce = {
            if (fold) {
                line("if not __started then __started = true; return __acc end")
            } else {
                line("if __acc == nil then local __f = __it(); __acc = __f; return __f end")
            }
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("__idx = __idx + 1")
            line("__acc = __fn(${if (indexed) "__idx, " else ""}__acc, __nilDecode(__v))")
            line("return __acc")
        },
    )

    private fun emitSequenceIfEmptyExpr(upstream: Expression, fallback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __head = __it()")
            line("if __head == nil then __it = (${callbackStr(fallback)}())(); __head = __it() end")
            line("local __started = false")
        },
        produce = {
            line("if not __started then __started = true; return __head end")
            line("return __it()")
        },
    )

    /** `sequence { yield(1); yieldAll(xs) }`: the body runs as a Lua coroutine and each yield hands one element out. */
    private fun emitSequenceBuilderExpr(body: LambdaExpr): String = emitSequenceExpr(
        setup = {
            line("local __co = coroutine.wrap(${emitLambda(body, noParams = true)})")
            line("local __done = false")
        },
        produce = {
            line("if __done then return nil end")
            line("local __v = __co()")
            line("if __v == nil then __done = true end")
            line("return __v")
        },
    )

    private fun emitGenerateSequenceNoSeedExpr(next: Expression): String = emitSequenceExpr(
        setup = { line("local __fn = ${callbackStr(next)}"); line("local __done = false") },
        produce = {
            line("if __done then return nil end")
            line("local __v = __fn()")
            line("if __v == nil then __done = true end")
            line("return __v")
        },
    )

    /** An element-by-element loop over [seqStr] whose body comes from [body], with `__fn` bound to [callback] first. */
    private fun sequenceLoop(seqStr: String, callback: Expression?, upstream: Expression, body: () -> Unit) {
        if (callback != null) line("local __fn = ${callbackStrFor(callback, upstream)}")
        line("for __raw in ($seqStr)() do")
        pushIndent()
        line("local __v = __nilDecode(__raw)")
        body()
        popIndent()
        line("end")
    }

    private fun emitSequenceExtreme(expr: MethodCallExpr, seqStr: String): String {
        val name = expr.methodName
        val isMax = name.startsWith("max")
        val orNull = name.endsWith("OrNull")
        val core = name.removePrefix(if (isMax) "max" else "min").removeSuffix("OrNull")
        val args = expr.args.map { it.toExpression() }
        val selector = when (core) { "By", "Of" -> args.getOrNull(0); "OfWith" -> args.getOrNull(1); else -> null }
        val comparator = when (core) { "With", "OfWith" -> args.getOrNull(0); else -> null }
        val returnsKey = core == "Of" || core == "OfWith"
        return emitSequenceTerminalExpr { r ->
            line("local $r, __best, __bk, __found = nil, nil, nil, false")
            if (comparator != null) line("local __cmp = ${emitExpression(comparator)}")
            sequenceLoop(seqStr, selector, expr.obj) {
                line("local __k = ${if (selector != null) "__fn(__v)" else "__v"}")
                val better = when {
                    comparator != null -> "__cmp(__k, __bk) ${if (isMax) ">" else "<"} 0"
                    else -> "__k ${if (isMax) ">" else "<"} __bk"
                }
                line("if not __found then __found = true; __best = __v; __bk = __k elseif $better then __best = __v; __bk = __k end")
            }
            line(if (orNull) "if __found then $r = ${if (returnsKey) "__bk" else "__best"} end"
                else "if not __found then error(\"Sequence is empty.\", 0) end; $r = ${if (returnsKey) "__bk" else "__best"}")
        }
    }

    /** Terminal operations that are not on List (or are better as short-circuiting loops); null when [expr] isn't one. */
    private fun emitSequenceEager(expr: MethodCallExpr, seqStr: String): String? {
        val m = expr.methodName
        val a = expr.args.map { it.toExpression() }
        val up = expr.obj
        fun terminal(body: (String) -> Unit): String = emitSequenceTerminalExpr(body)
        return when {
            m in SEQUENCE_EXTREME_NAMES -> emitSequenceExtreme(expr, seqStr)
            (m == "single" || m == "singleOrNull") && a.size <= 1 -> terminal { r ->
                line("local $r, __n = nil, 0")
                sequenceLoop(seqStr, a.firstOrNull(), up) {
                    line(if (a.isEmpty()) "__n = __n + 1; $r = __v; if __n > 1 then break end"
                        else "if __fn(__v) then __n = __n + 1; $r = __v; if __n > 1 then break end end")
                }
                line(if (m == "single") "if __n ~= 1 then error(__n == 0 and \"Sequence is empty.\" or \"Sequence has more than one element.\", 0) end"
                    else "if __n ~= 1 then $r = nil end")
            }
            (m == "elementAt" || m == "elementAtOrNull" || m == "elementAtOrElse") && a.isNotEmpty() -> terminal { r ->
                line("local $r, __i, __found = nil, 0, false")
                line("local __n = ${emitExpression(a[0])}")
                sequenceLoop(seqStr, null, up) { line("if __i == __n then $r = __v; __found = true; break end; __i = __i + 1") }
                when (m) {
                    "elementAt" -> line("if not __found then error(\"Sequence doesn't contain element at index \" .. tostring(__n) .. \".\", 0) end")
                    "elementAtOrElse" -> line("if not __found then $r = (${callbackStr(a[1])})(__n) end")
                    else -> {}
                }
            }
            (m == "indexOf" || m == "lastIndexOf") && a.size == 1 -> terminal { r ->
                line("local $r, __i = -1, 0")
                line("local __x = ${emitExpression(a[0])}")
                sequenceLoop(seqStr, null, up) {
                    line("if ${structuralEq(a[0], "__v", "__x")} then $r = __i${if (m == "indexOf") "; break" else ""} end")
                    line("__i = __i + 1")
                }
            }
            (m == "indexOfFirst" || m == "indexOfLast") && a.size == 1 -> terminal { r ->
                line("local $r, __i = -1, 0")
                sequenceLoop(seqStr, a[0], up) {
                    line("if __fn(__v) then $r = __i${if (m == "indexOfFirst") "; break" else ""} end")
                    line("__i = __i + 1")
                }
            }
            m == "contains" && a.size == 1 -> terminal { r ->
                line("local $r = false")
                line("local __x = ${emitExpression(a[0])}")
                sequenceLoop(seqStr, null, up) { line("if ${structuralEq(a[0], "__v", "__x")} then $r = true; break end") }
            }
            (m == "last" || m == "lastOrNull" || m == "findLast") && a.size <= 1 -> terminal { r ->
                line("local $r, __found = nil, false")
                sequenceLoop(seqStr, a.firstOrNull(), up) {
                    line(if (a.isEmpty()) "$r = __v; __found = true" else "if __fn(__v) then $r = __v; __found = true end")
                }
                if (m == "last") line("if not __found then error(\"Sequence is empty.\", 0) end")
            }
            m in setOf("reduce", "reduceOrNull", "reduceIndexed", "reduceIndexedOrNull") && a.size == 1 -> terminal { r ->
                val indexed = m.contains("Indexed")
                line("local $r, __found, __i = nil, false, 0")
                sequenceLoop(seqStr, a[0], up) {
                    line("if not __found then $r = __v; __found = true else __i = __i + 1; $r = __fn(${if (indexed) "__i, " else ""}$r, __v) end")
                }
                if (!m.endsWith("OrNull")) line("if not __found then error(\"Empty sequence can't be reduced.\", 0) end")
            }
            m == "foldIndexed" && a.size == 2 -> terminal { r ->
                line("local $r, __i = ${emitExpression(a[0])}, -1")
                sequenceLoop(seqStr, a[1], up) { line("__i = __i + 1; $r = __fn(__i, $r, __v)") }
            }
            (m == "sumBy" || m == "sumByDouble") && a.size == 1 -> terminal { r ->
                line("local $r = 0")
                sequenceLoop(seqStr, a[0], up) { line("$r = $r + __fn(__v)") }
            }
            m == "average" && a.isEmpty() -> terminal { r ->
                line("local __s, __c = 0, 0")
                sequenceLoop(seqStr, null, up) { line("__s = __s + __v; __c = __c + 1") }
                line("local $r = __c == 0 and 0 / 0 or __s / __c")
            }
            m == "unzip" && a.isEmpty() -> terminal { r ->
                line("local __a, __b = {}, {}")
                sequenceLoop(seqStr, null, up) { line("__a[#__a + 1] = __v.first; __b[#__b + 1] = __v.second") }
                line("local $r = {first = __a, second = __b}")
            }
            (m == "allDistinct" || m == "allDistinctBy") && a.size == (if (m == "allDistinct") 0 else 1) -> terminal { r ->
                line("local $r, __seen = true, {}")
                sequenceLoop(seqStr, a.firstOrNull(), up) {
                    line("local __k = ${if (a.isEmpty()) "__v" else "__fn(__v)"}")
                    line("if __seen[__k] then $r = false; break end; __seen[__k] = true")
                }
            }
            (m == "allEqual" || m == "allEqualBy") && a.size == (if (m == "allEqual") 0 else 1) -> terminal { r ->
                line("local $r, __first, __has = true, nil, false")
                sequenceLoop(seqStr, a.firstOrNull(), up) {
                    line("local __k = ${if (a.isEmpty()) "__v" else "__fn(__v)"}")
                    line("if __has and __k ~= __first then $r = false; break end; __first = __k; __has = true")
                }
            }
            m in setOf("isSorted", "isSortedDescending", "isSortedBy", "isSortedByDescending", "isSortedWith") &&
                a.size == (if (m == "isSorted" || m == "isSortedDescending") 0 else 1) -> terminal { r ->
                line("local $r, __prev, __has = true, nil, false")
                if (m == "isSortedWith") line("local __cmp = ${emitExpression(a[0])}")
                val keyed = m == "isSortedBy" || m == "isSortedByDescending"
                sequenceLoop(seqStr, if (keyed) a[0] else null, up) {
                    line("local __k = ${if (keyed) "__fn(__v)" else "__v"}")
                    val outOfOrder = when (m) {
                        "isSortedWith" -> "__cmp(__prev, __k) > 0"
                        "isSortedDescending", "isSortedByDescending" -> "__k > __prev"
                        else -> "__k < __prev"
                    }
                    line("if __has and $outOfOrder then $r = false; break end; __prev = __k; __has = true")
                }
            }
            (m == "firstNotNullOf" || m == "firstNotNullOfOrNull") && a.size == 1 -> terminal { r ->
                line("local $r = nil")
                sequenceLoop(seqStr, a[0], up) { line("local __k = __fn(__v); if __k ~= nil then $r = __k; break end") }
                if (m == "firstNotNullOf") line("if $r == nil then error(\"No element of the sequence was transformed to a non-null value.\", 0) end")
            }
            m in setOf("toMutableSet", "toHashSet") && a.isEmpty() -> terminal { emitSequenceToSetStatements(it, seqStr) }
            m == "toSortedSet" && a.isEmpty() -> terminal { r ->
                emitSequenceToSetStatements(r, seqStr)
                line("table.sort($r.list)")
            }
            m == "asIterable" && a.isEmpty() -> terminal { emitSequenceToListStatements(it, seqStr) }
            m == "toCollection" && a.size == 1 -> terminal { r ->
                line("local $r = ${emitExpression(a[0])}")
                sequenceLoop(seqStr, null, up) { emitAddToDestination(r, receiverInstanceClass(a[0]), "__v") }
            }
            m == "shuffled" && a.size <= 1 -> "(function() local __t = {}; for __v in ($seqStr)() do __t[#__t + 1] = __v end; " +
                "for __i = #__t, 2, -1 do local __j = math.random(__i); __t[__i], __t[__j] = __t[__j], __t[__i] end; " +
                "return function() local __n = 0; return function() __n = __n + 1; return __t[__n] end end end)()"
            m == "iterator" && a.isEmpty() -> iteratorObject(seqStr)
            m == "joinTo" && a.isNotEmpty() -> {
                val plain = expr.args.filter { it.toExpression() !is LambdaExpr }
                val transform = expr.args.filterIsInstance<NamedArg>().firstOrNull { it.name == "transform" }?.expr
                    ?: expr.args.map { it.toExpression() }.filterIsInstance<LambdaExpr>().firstOrNull()
                fun arg(name: String, position: Int) = resolveNamedOrPositional(plain, name, position)?.let { emitExpression(it) }
                terminal { r ->
                    line("local $r = ${arg("buffer", 0)}")
                    line("local __sep, __post, __limit, __trunc = ${arg("separator", 1) ?: "\", \""}, ${arg("postfix", 3) ?: "\"\""}, ${arg("limit", 4) ?: "-1"}, ${arg("truncated", 5) ?: "\"...\""}")
                    line("table.insert($r.buf, ${arg("prefix", 2) ?: "\"\""})")
                    line("local __count = 0")
                    sequenceLoop(seqStr, transform, up) {
                        line("__count = __count + 1")
                        line("if __count > 1 then table.insert($r.buf, __sep) end")
                        line("if __limit < 0 or __count <= __limit then table.insert($r.buf, tostring(${if (transform != null) "__fn(__v)" else "__v"})) else break end")
                    }
                    line("if __limit >= 0 and __count > __limit then table.insert($r.buf, __trunc) end")
                    line("table.insert($r.buf, __post)")
                }
            }
            else -> null
        }
    }

    private fun emitAddToDestination(destVar: String, destClass: String?, valueStr: String) {
        if (destClass == "Set") {
            emitSetAdd(destVar, valueStr)
        } else {
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            line("table.insert($destVar, __nilEncode($valueStr))")
        }
    }

    /** An Iterator over a sequence factory: `hasNext()` / `next()` called with colon syntax. */
    private fun iteratorObject(seqStr: String): String =
        "(function() local __it = ($seqStr)(); local __nx = __it(); return {" +
            "hasNext = function(self) return __nx ~= nil end, " +
            "next = function(self) local __v = __nx; if __v == nil then error(\"No more elements.\", 0) end; __nx = __it(); return __nilDecode(__v) end} end)()"

    /** A Lua predicate for `is T` on a runtime value. Int/Long/Double are all plain Lua numbers, so they can't be told apart. */
    private fun instancePredicate(typeName: String): String = when (typeName) {
        "Int", "Long", "Short", "Byte", "Double", "Float", "Number", "UInt", "ULong" -> "function(v) return type(v) == \"number\" end"
        "String", "Char", "CharSequence" -> "function(v) return type(v) == \"string\" end"
        "Boolean" -> "function(v) return type(v) == \"boolean\" end"
        "Any" -> "function(v) return true end"
        else -> "function(v) local m = getmetatable(v); while m do if m == $typeName then return true end; local n = getmetatable(m); m = n and n.__index end; return false end"
    }

    /** filterTo / mapTo / groupByTo / associateTo ... : run the operation and write into the destination collection. */
    private fun emitSequenceToVariant(expr: MethodCallExpr, seqStr: String): String {
        val m = expr.methodName
        val a = expr.args.map { it.toExpression() }
        val dest = a[0]
        val destClass = receiverInstanceClass(dest)
        val indexed = m.contains("Indexed")
        val fnExpr = a.getOrNull(1)
        val transform = a.getOrNull(2)
        return emitSequenceTerminalExpr { r ->
            line("local $r = ${emitExpression(dest)}")
            if (indexed) line("local __i = -1")
            if (transform != null) line("local __fn2 = ${callbackStrFor(transform, expr.obj)}")
            fun add(valueStr: String) = emitAddToDestination(r, destClass, valueStr)
            sequenceLoop(seqStr, if (m == "filterIsInstanceTo") null else fnExpr, expr.obj) {
                if (indexed) line("__i = __i + 1")
                val idx = if (indexed) "__i, " else ""
                when (m) {
                    "filterTo" -> { line("if __fn(__v) then"); pushIndent(); add("__v"); popIndent(); line("end") }
                    "filterNotTo" -> { line("if not __fn(__v) then"); pushIndent(); add("__v"); popIndent(); line("end") }
                    "filterIndexedTo" -> { line("if __fn(${idx}__v) then"); pushIndent(); add("__v"); popIndent(); line("end") }
                    "filterIsInstanceTo" -> {
                        line("if (${instancePredicate(expr.typeArgs.firstOrNull() ?: "Any")})(__v) then"); pushIndent(); add("__v"); popIndent(); line("end")
                    }
                    "filterNotNullTo" -> add("__v")
                    "mapTo", "mapIndexedTo" -> add("__fn(${idx}__v)")
                    "mapNotNullTo", "mapIndexedNotNullTo" -> {
                        line("local __r = __fn(${idx}__v)"); line("if __r ~= nil then"); pushIndent(); add("__r"); popIndent(); line("end")
                    }
                    "flatMapTo", "flatMapIndexedTo" -> {
                        line("local __r = __fn(${idx}__v)")
                        line("if type(__r) == \"function\" then")
                        pushIndent(); line("for __x in __r() do"); pushIndent(); add("__x"); popIndent(); line("end"); popIndent()
                        line("else")
                        pushIndent(); line("for _, __x in ipairs(__r.list or __r) do"); pushIndent(); add("__x"); popIndent(); line("end"); popIndent()
                        line("end")
                    }
                    "groupByTo" -> {
                        line("local __k = __fn(__v)")
                        line("if $r.map[__k] == nil then table.insert($r.keys, __k); $r.map[__k] = {} end")
                        line("table.insert($r.map[__k], ${if (transform != null) "__fn2(__v)" else "__v"})")
                    }
                    "associateTo" -> {
                        line("local __p = __fn(__v)")
                        emitMapPut(r, "__p.first", "__p.second", null)
                    }
                    "associateByTo" -> emitMapPut(r, "__fn(__v)", if (transform != null) "__fn2(__v)" else "__v", null)
                    "associateWithTo" -> emitMapPut(r, "__v", "__fn(__v)", null)
                    else -> error("Unsupported sequence operation $m")
                }
            }
        }
    }

    /** `seq.groupingBy { key }`: the elements plus the key function; eachCount / fold / reduce / aggregate read them. */
    private fun emitSequenceGroupingBy(upstream: Expression, keySelector: Expression): String =
        "(function() local __items = {}; for __v in (${emitExpression(upstream)})() do __items[#__items + 1] = __v end; " +
            "return {items = __items, keyOf = ${callbackStrFor(keySelector, upstream)}} end)()"

    private fun emitGroupingCall(expr: MethodCallExpr): String? {
        val a = expr.args.map { it.toExpression() }
        val g = emitExpression(expr.obj)
        return when {
            expr.methodName == "eachCount" && a.isEmpty() ->
                "(function(g) local __r = {keys = {}, map = {}}; for _, __v in ipairs(g.items) do local __k = g.keyOf(__nilDecode(__v)); " +
                    "if __r.map[__k] == nil then table.insert(__r.keys, __k); __r.map[__k] = 0 end; __r.map[__k] = __r.map[__k] + 1 end; return __r end)($g)"
            expr.methodName == "fold" && a.size == 2 ->
                "(function(g, init, op) local __r = {keys = {}, map = {}}; for _, __v in ipairs(g.items) do local __k = g.keyOf(__nilDecode(__v)); " +
                    "if __r.map[__k] == nil then table.insert(__r.keys, __k); __r.map[__k] = init end; __r.map[__k] = op(__r.map[__k], __nilDecode(__v)) end; return __r end)" +
                    "($g, ${emitExpression(a[0])}, ${callbackStr(a[1])})"
            expr.methodName == "reduce" && a.size == 1 ->
                "(function(g, op) local __r = {keys = {}, map = {}}; for _, __v in ipairs(g.items) do local __k = g.keyOf(__nilDecode(__v)); " +
                    "if __r.map[__k] == nil then table.insert(__r.keys, __k); __r.map[__k] = __v else __r.map[__k] = op(__k, __r.map[__k], __nilDecode(__v)) end end; return __r end)" +
                    "($g, ${callbackStr(a[0])})"
            expr.methodName == "aggregate" && a.size == 1 ->
                "(function(g, op) local __r = {keys = {}, map = {}}; for _, __v in ipairs(g.items) do local __k = g.keyOf(__nilDecode(__v)); local __first = __r.map[__k] == nil; " +
                    "if __first then table.insert(__r.keys, __k) end; __r.map[__k] = op(__k, __r.map[__k], __nilDecode(__v), __first) end; return __r end)" +
                    "($g, ${callbackStr(a[0])})"
            else -> null
        }
    }

    private fun emitSequenceToListStatements(resultVar: String, seqStr: String) {
        line("local $resultVar = {}")
        line("for __v in ($seqStr)() do table.insert($resultVar, __v) end")
    }

    private fun emitSequenceToSetStatements(resultVar: String, seqStr: String) {
        line("local $resultVar = {list = {}, has = {}}")
        line("for __v in ($seqStr)() do")
        pushIndent()
        emitSetAdd(resultVar, "__v")
        popIndent()
        line("end")
    }

    private fun emitSequenceForEachStatement(seqStr: String, callback: Expression) {
        if (callback is LambdaExpr) {
            val paramName = callback.params.firstOrNull() ?: "it"
            types.pushScope()
            types.env.declare(paramName, TypeName.ANY)
            line("for __raw in ($seqStr)() do")
            pushIndent()
            line("local $paramName = __nilDecode(__raw)")
            callback.body.body.forEach { emitStatement(it) }
            popIndent()
            line("end")
            types.popScope()
        } else {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(callback)}")
            line("for __v in ($seqStr)() do $fnVar(__nilDecode(__v)) end")
        }
    }

    private fun emitSequenceFindStatements(resultVar: String, seqStr: String, callback: Expression) {
        line("local $resultVar = nil")
        if (callback is LambdaExpr) {
            val paramName = callback.params.firstOrNull() ?: "it"
            types.pushScope()
            types.env.declare(paramName, TypeName.ANY)
            line("for __raw in ($seqStr)() do")
            pushIndent()
            line("local $paramName = __nilDecode(__raw)")
            val stmts = callback.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) emitLambdaResult(stmt) { "if $it then $resultVar = $paramName; break end" } else emitStatement(stmt)
            }
            popIndent()
            line("end")
            types.popScope()
        } else {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(callback)}")
            line("for __v in ($seqStr)() do if $fnVar(__nilDecode(__v)) then $resultVar = __nilDecode(__v); break end end")
        }
    }

    private fun emitSequenceCountStatements(resultVar: String, seqStr: String) {
        line("local $resultVar = 0")
        line("for __v in ($seqStr)() do $resultVar = $resultVar + 1 end")
    }

    private fun emitSequenceSumStatements(resultVar: String, seqStr: String) {
        line("local $resultVar = 0")
        line("for __v in ($seqStr)() do $resultVar = $resultVar + __v end")
    }

    private fun emitSequenceAnyAllNoneStatements(resultVar: String, seqStr: String, callback: Expression, mode: String) {
        val initial = if (mode == "any") "false" else "true"
        line("local $resultVar = $initial")
        val sink: (String) -> String = when (mode) {
            "any" -> { v -> "if $v then $resultVar = true; break end" }
            "all" -> { v -> "if not ($v) then $resultVar = false; break end" }
            else -> { v -> "if $v then $resultVar = false; break end" }
        }
        if (callback is LambdaExpr) {
            val paramName = callback.params.firstOrNull() ?: "it"
            types.pushScope()
            types.env.declare(paramName, TypeName.ANY)
            line("for __raw in ($seqStr)() do")
            pushIndent()
            line("local $paramName = __nilDecode(__raw)")
            val stmts = callback.body.body
            stmts.forEachIndexed { i, stmt -> if (i == stmts.lastIndex) emitLambdaResult(stmt, sink) else emitStatement(stmt) }
            popIndent()
            line("end")
            types.popScope()
        } else {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(callback)}")
            line("for __v in ($seqStr)() do")
            pushIndent()
            line(sink("$fnVar(__nilDecode(__v))"))
            popIndent()
            line("end")
        }
    }

    private fun emitSequenceFoldStatements(resultVar: String, seqStr: String, seed: Expression, callback: Expression) {
        if (callback is LambdaExpr) {
            val accParam = callback.params.getOrElse(0) { "acc" }
            val xParam = callback.params.getOrElse(1) { "it" }
            line("local $accParam = ${emitExpression(seed)}")
            types.pushScope()
            types.env.declare(accParam, TypeName.ANY)
            types.env.declare(xParam, TypeName.ANY)
            line("for __raw in ($seqStr)() do")
            pushIndent()
            line("local $xParam = __nilDecode(__raw)")
            val stmts = callback.body.body
            stmts.forEachIndexed { i, stmt ->
                if (i == stmts.lastIndex) emitLambdaResult(stmt) { "$accParam = $it" } else emitStatement(stmt)
            }
            popIndent()
            line("end")
            types.popScope()
            if (resultVar != accParam) line("local $resultVar = $accParam")
        } else {
            line("local $resultVar = ${emitExpression(seed)}")
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(callback)}")
            line("for __v in ($seqStr)() do $resultVar = $fnVar($resultVar, __nilDecode(__v)) end")
        }
    }

    private fun resolveNamedOrPositional(args: List<CallArg>, name: String, position: Int): Expression? {
        args.filterIsInstance<NamedArg>().firstOrNull { it.name == name }?.let { return it.expr }
        return args.filterIsInstance<PositionalArg>().getOrNull(position)?.expr
    }

    private fun emitJoinToStringExpr(receiverStr: String, args: List<CallArg>): String {
        val sep = resolveNamedOrPositional(args, "separator", 0)?.let { emitExpression(it) } ?: "\", \""
        val prefix = resolveNamedOrPositional(args, "prefix", 1)?.let { emitExpression(it) } ?: "\"\""
        val postfix = resolveNamedOrPositional(args, "postfix", 2)?.let { emitExpression(it) } ?: "\"\""
        return "($prefix .. table.concat((function() local __s = {} for _, __v in ipairs($receiverStr) do table.insert(__s, tostring(__v)) end return __s end)(), $sep) .. $postfix)"
    }

    override fun iterationArrayOf(obj: Expression): String {
        val str = emitExpression(obj)
        return if (receiverInstanceClass(obj) == "Set") "$str.list" else str
    }


    private fun emitForEachIndexedStatement(obj: Expression, callback: LambdaExpr) {
        val cls = receiverInstanceClass(obj) ?: "List"
        val call = OperationCall(obj, listOf(PositionalArg(callback)))
        operationRegistry.resolve(cls, "forEachIndexed", call, this)?.lower(call, null, this)
    }


    private fun emitInlineAccumulate(expr: MethodCallExpr, resultVar: String, elementClass: String?): Boolean {
        val cls = receiverInstanceClass(expr.obj)
        val call = OperationCall(expr.obj, expr.args, elementClass)
        operationRegistry.resolve(cls ?: "List", expr.methodName, call, this)?.takeIf { it.needsResultVar() }
            ?.let { return it.lower(call, resultVar, this) }
        return false
    }

    private fun emitCollectionIIFE(resultVar: String?, body: () -> Boolean): String? {
        val savedOut = out
        val savedIndent = indentLevel
        out = StringBuilder()
        indentLevel = savedIndent + 1
        val ok = body()
        if (ok && resultVar != null) line("return $resultVar")
        val bodyLua = out.toString()
        out = savedOut
        indentLevel = savedIndent
        if (!ok) return null
        return "(function()\n${bodyLua}${indentUnit.repeat(savedIndent)}end)()"
    }

    private fun emitSequenceTerminalExpr(body: (resultVar: String) -> Unit): String {
        useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        val resultVar = nextTemp("__result")
        return emitCollectionIIFE(resultVar) { body(resultVar); true }!!
    }

    private fun emitNotNullAssert(expr: NotNullAssertExpr): String =
        "(function()\n" +
            "${indentUnit}local __v = ${emitExpression(expr.expr)}\n" +
            "${indentUnit}if __v == nil then error(\"!! assertion failed: value was null\") end\n" +
            "${indentUnit}return __v\n" +
            "end)()"

    private fun emitElvis(expr: ElvisExpr): String =
        "(function()\n" +
            "${indentUnit}local __v = ${emitExpression(expr.left)}\n" +
            "${indentUnit}if __v ~= nil then return __v end\n" +
            "${indentUnit}return ${emitExpression(expr.right)}\n" +
            "end)()"

    private fun emitSafeChain(expr: SafeChainExpr): String {
        val temporary = "__v"
        val sb = StringBuilder("(function()\n")
        sb.append("${indentUnit}local $temporary = ${emitExpression(expr.base)}\n")
        types.pushScope()
        types.env.declare(temporary, TypeName.ANY)
        var current = expr.base
        for (step in expr.steps) {
            val safe = when (step) {
                is ChainProp -> step.safe
                is ChainCall -> step.safe
            }
            if (safe) sb.append("${indentUnit}if $temporary == nil then return nil end\n")
            types.forgetInstance(temporary)
            receiverInstanceClass(current)?.let { types.declareInstance(temporary, it) }
            when (step) {
                is ChainProp -> {
                    val hostName = expressionPath(PropertyAccessExpr(current, step.name))?.let { bindings.hostPath(it) }?.substringAfterLast('.') ?: step.name
                    sb.append("${indentUnit}$temporary = ${emitExpression(PropertyAccessExpr(Identifier(temporary), hostName))}\n")
                    current = PropertyAccessExpr(current, step.name)
                }
                is ChainCall -> {
                    val hostName = expressionPath(PropertyAccessExpr(current, step.name))?.let { bindings.hostPath(it) }?.substringAfterLast('.') ?: step.name
                    sb.append("${indentUnit}$temporary = ${emitMethodCallTarget(MethodCallExpr(Identifier(temporary), hostName, step.args))}\n")
                    current = MethodCallExpr(current, step.name, step.args)
                }
            }
        }
        types.popScope()
        sb.append("${indentUnit}return $temporary\n")
        sb.append("end)()")
        return sb.toString()
    }

    // A data class with no hand-written toString() gets an auto-generated __tostring listing
    // every constructor property, so comparing tostring(a) == tostring(b) is a valid stand-in for
    // field-by-field equality (mirrors the same tostring-as-structural-key trick StructuralKeying
    // already uses for map keys/collection search). A hand-written toString() might not mention
    // every property, so it's excluded - only the auto-generated one is guaranteed complete.
    private fun isAutoDataClass(expr: Expression): Boolean =
        receiverInstanceClass(expr)?.let { cls -> types.classInfo(cls)?.let { it.isData && !it.hasToString } } == true

    private fun emitBinary(expr: BinaryExpr): String {
        val leftStr = emitExpression(expr.left)
        val rightStr = emitExpression(expr.right)
        return when (expr.op) {
            "&&" -> "($leftStr and $rightStr)"
            "||" -> "($leftStr or $rightStr)"
            "==" -> when {
                receiverInstanceClass(expr.left) in setOf("List", "Set", "Map") -> emitCollectionEquals(expr.left, leftStr, rightStr)
                isAutoDataClass(expr.left) -> "(tostring($leftStr) == tostring($rightStr))"
                else -> "($leftStr == $rightStr)"
            }
            "!=" -> when {
                receiverInstanceClass(expr.left) in setOf("List", "Set", "Map") -> "(not ${emitCollectionEquals(expr.left, leftStr, rightStr)})"
                isAutoDataClass(expr.left) -> "(tostring($leftStr) ~= tostring($rightStr))"
                else -> "($leftStr ~= $rightStr)"
            }
            "in" -> inRangeOrContains(expr, leftStr, rightStr)
            "!in" -> "(not ${inRangeOrContains(expr, leftStr, rightStr)})"
            "+" -> when (receiverInstanceClass(expr.left)) {
                "Sequence" -> emitSequencePlusExpr(expr.left, expr.right, element = false)
                "List" -> emitListPlusExpr(leftStr, expr.right, rightStr)
                "Set" -> emitSetPlusMinusExpr(leftStr, expr.right, rightStr, removing = false)
                else -> if (types.inferExprType(expr.left) == TypeName.CHAR) {
                    "string.char(string.byte($leftStr) + $rightStr)"
                } else if (types.inferExprType(expr.left) == TypeName.STRING || types.inferExprType(expr.right) == TypeName.STRING) {
                    "(${concatOperand(expr.left, leftStr)} .. ${concatOperand(expr.right, rightStr)})"
                } else {
                    "($leftStr + $rightStr)"
                }
            }
            "-" -> when (receiverInstanceClass(expr.left)) {
                "Sequence" -> emitSequenceMinusExpr(expr.left, expr.right, element = false)
                "List" -> emitListMinusExpr(leftStr, expr.right, rightStr)
                "Set" -> emitSetPlusMinusExpr(leftStr, expr.right, rightStr, removing = true)
                else -> if (types.inferExprType(expr.left) != TypeName.CHAR) "($leftStr - $rightStr)"
                    else if (types.inferExprType(expr.right) == TypeName.CHAR) "(string.byte($leftStr) - string.byte($rightStr))"
                    else "string.char(string.byte($leftStr) - $rightStr)"
            }
            "/" -> {
                val lt = types.inferExprType(expr.left)
                val rt = types.inferExprType(expr.right)
                if (lt == TypeName.INT && rt == TypeName.INT) {
                    "(math.modf($leftStr / $rightStr))"
                } else {
                    "($leftStr / $rightStr)"
                }
            }
            "%" -> {
                "math.fmod($leftStr, $rightStr)"
            }
            else -> "($leftStr ${expr.op} $rightStr)"
        }
    }

    private fun emitCall(expr: CallExpr): String {
        if (expr.callee == "StringBuilder" && expr.args.size <= 1 && types.classInfo("StringBuilder")?.native == true) {
            val initial = expr.args.firstOrNull()?.toExpression()?.takeIf { types.inferExprType(it) != TypeName.INT }
            return "{buf = {${initial?.let { formatForToString(it, emitExpression(it)) } ?: ""}}}"
        }
        if (expr.callee == "Random" && expr.args.size == 1 && types.classInfo("Random")?.native == true) {
            useRuntimeHelper(RuntimeHelper.RANDOM)
            return "__Random.new(${emitExpression(expr.args[0].toExpression())})"
        }
        if (expr.args.any { it is NamedArg } && types.classInfo(expr.callee) == null) {
            types.functionParamNames(expr.callee)?.let { names ->
                val positional = expr.args.takeWhile { it !is NamedArg }
                val named = expr.args.drop(positional.size).filterIsInstance<NamedArg>().associateBy { it.name }
                val rest = names.drop(positional.size).map { name ->
                    PositionalArg(named[name]?.expr ?: NullLiteral.takeIf { name in types.functionDefaultedParams(expr.callee) }
                        ?: error("Missing argument '$name' in call to ${expr.callee}"))
                }
                return emitCall(expr.copy(args = positional + rest))
            }
        }
        objectMemberPrefix(expr.callee, objectFuns)?.let { return "$it.${expr.callee}(${emitArgList(expr.args)})" }
        if (expr.callee == "Pair" && expr.args.size == 2 && types.classInfo("Pair")?.native != false && expr.args.all { it is PositionalArg }) {
            return emitExpression(ToPairExpr(expr.args[0].toExpression(), expr.args[1].toExpression()))
        }
        types.memberReceiver(expr.callee)?.let { receiver ->
            val className = types.instanceClassOf(receiver)
            val info = className?.let { types.classInfo(it) }
            val colon = if (info?.native == true) bindings.method(className, expr.callee)?.first == true
                else expr.callee in (info?.methodNames ?: emptySet())
            val method = if (info?.native == true) bindings.methodName(className!!, expr.callee) else expr.callee
            return "$receiver${if (colon) ":" else "."}$method(${emitArgList(expr.args)})"
        }
        if (expr.callee == "runCatching" && expr.args.size == 1) {
            usedRuntimeHelpers += RuntimeHelper.RESULT
            val block = expr.args.single().toExpression()
            val function = if (block is LambdaExpr) emitLambda(block, noParams = true) else emitExpression(block)
            return "__runCatching($function)"
        }
        if (expr.callee == "run" && expr.args.size == 1) {
            val block = expr.args.single().toExpression()
            val function = if (block is LambdaExpr) emitLambda(block, noParams = true) else emitExpression(block)
            return "($function)()"
        }
        operationRegistry.resolveTopLevel(expr.callee, OperationCall(null, expr.args), this)?.let { op ->
            return if (op.resultKind(null) == "List" && expr.args.none { it is SpreadArg }) {
                if (expr.args.isNotEmpty()) useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
                "{${expr.args.joinToString(", ") { "__nilEncode(${emitExpression(it.toExpression())})" }}}"
            } else {
                val resultVar = nextTemp("__coll")
                emitCollectionIIFE(resultVar) { op.lower(OperationCall(null, expr.args), resultVar, this) }!!
            }
        }
        if (expr.callee in setOf("compareBy", "compareByDescending") && expr.args.size == 1) {
            collectComparatorChain(expr)?.let { return emitComparatorChainExpr(it) }
        }
        if (expr.callee == "sequenceOf") {
            return emitSequenceOfExpr(expr.args)
        }
        if (expr.callee == "generateSequence" && expr.args.size == 1 && types.functionParamNames("generateSequence") == null) {
            return emitGenerateSequenceNoSeedExpr(expr.args[0].toExpression())
        }
        if (expr.callee == "generateSequence" && expr.args.size == 2) {
            val first = expr.args[0].toExpression()
            val seed = if (first is LambdaExpr) first.copy(params = emptyList()).let { MethodCallExpr(it, "invoke", emptyList()) } else first
            return emitGenerateSequenceExpr(seed, expr.args[1].toExpression())
        }
        if (expr.callee == "emptySequence" && expr.args.isEmpty() && types.functionParamNames("emptySequence") == null) {
            return "function() return function() return nil end end"
        }
        if (expr.callee == "sequence" && expr.args.size == 1 && types.functionParamNames("sequence") == null) {
            (expr.args[0].toExpression() as? LambdaExpr)?.let { return emitSequenceBuilderExpr(it) }
        }
        if (expr.callee == "iterator" && expr.args.size == 1 && types.functionParamNames("iterator") == null) {
            (expr.args[0].toExpression() as? LambdaExpr)?.let { return iteratorObject(emitSequenceBuilderExpr(it)) }
        }
        if (expr.callee == "yield" && expr.args.size == 1 && types.functionParamNames("yield") == null) {
            useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
            return "coroutine.yield(__nilEncode(${emitExpression(expr.args[0].toExpression())}))"
        }
        if (expr.callee == "yieldAll" && expr.args.size == 1 && types.functionParamNames("yieldAll") == null) {
            val arg = expr.args[0].toExpression()
            return if (receiverInstanceClass(arg) == "Sequence") "(function() for __y in (${emitExpression(arg)})() do coroutine.yield(__y) end end)()"
            else "(function() for _, __y in ipairs(${iterationArrayOf(arg)}) do coroutine.yield(__y) end end)()"
        }
        if (expr.callee == "with" && expr.args.size == 2) {
            val receiver = expr.args[0].toExpression()
            val lambdaArg = expr.args[1].toExpression()
            if (lambdaArg is LambdaExpr) {
                val fnStr = emitLambda(lambdaArg.copy(params = emptyList()), receiverInstanceClass(receiver),
                    implicitParamName = receiverParameterName(), receiverScope = true)
                return "($fnStr)(${emitExpression(receiver)})"
            }
        }
        nativeFactory(expr.callee)?.let { return "$it(${emitArgList(expr.args)})" }
        bindings.recordFields(expr.callee)?.let { fields ->
            val canonical = bindings.type(expr.callee)!!
            return emitNamedArgTable(recordArguments(expr.callee, expr.args).map { it.copy(name = bindings.propertyName(canonical, it.name)) })
        }
        types.classInfo(expr.callee)?.takeUnless { it.native }?.let { return emitConstructorCall(expr, it) }
        if (expr.args.isNotEmpty() && expr.args.all { it is NamedArg } && expr.callee.first().isUpperCase()) {
            return emitArgList(expr.args)
        }
        val positional = expr.args.map { emitExpression(it.toExpression()) }
        if (types.functionParamNames(expr.callee) == null) emitMathCall(expr.callee, positional, expr.args.map { it.toExpression() })?.let { return it }
        return when (expr.callee) {
            "println" -> "print(${expr.args.mapIndexed { i, arg -> collectionAwareOrPlain(arg.toExpression(), positional[i]) }.joinToString(", ")})"
            "print" -> "io.write(${if (expr.args.isEmpty()) "\"\"" else formatForToString(expr.args[0].toExpression(), positional[0])})"
            else -> "${expr.callee}(${emitArgList(expr.args)})"
        }
    }

    /** `Random.nextInt(...)` and friends called directly on the default Random go straight to Lua's
     * `math.random`, so no runtime helper is emitted unless a seeded Random (or the default one as a
     * value) is actually used. */
    /** A range argument (`nextInt(1..6)`) becomes its two bounds, end-exclusive like `nextInt(from, until)`. */
    private fun randomArguments(args: List<CallArg>): Pair<List<String>, List<Expression>> {
        val strs = mutableListOf<String>()
        val exprs = mutableListOf<Expression>()
        for (arg in args) {
            val e = arg.toExpression()
            if (e is RangeExpr && !e.descending) {
                strs += emitExpression(e.start)
                exprs += e.start
                val end = emitExpression(e.end)
                strs += if (e.inclusive) "($end + 1)" else end
                exprs += if (e.inclusive) BinaryExpr("+", e.end, IntLiteral(1)) else e.end
            } else {
                strs += emitExpression(e)
                exprs += e
            }
        }
        return strs to exprs
    }

    /** Calls on the default Random go straight to Lua's `math.random`, so no runtime helper is emitted
     * unless a seeded Random (or the default one as a value) is actually used. */
    private fun emitDefaultRandomCall(name: String, args: List<String>, exprs: List<Expression>): String {
        val whole = "(math.random(0, 65535) * 65536 + math.random(0, 65535))"
        val bits53 = "(math.random(0, 67108863) * 134217728 + math.random(0, 134217727))"
        return when {
            name == "nextBoolean" -> "(math.random() < 0.5)"
            name == "nextInt" && args.isEmpty() -> "($whole - 2147483648)"
            name == "nextInt" && args.size == 1 -> "math.random(0, ${args[0]} - 1)"
            name == "nextInt" -> "math.random(${args[0]}, ${args[1]} - 1)"
            name == "nextDouble" && args.isEmpty() -> "math.random()"
            name == "nextDouble" && args.size == 1 -> "(math.random() * ${args[0]})"
            name == "nextDouble" -> inlineOnce(exprs, args) { (lo, hi) -> "($lo + math.random() * ($hi - $lo))" }
            name == "nextUInt" && args.isEmpty() -> whole
            name == "nextLong" && args.isEmpty() -> "($bits53 - 4503599627370496)"
            name == "nextULong" && args.isEmpty() -> bits53
            name in setOf("nextUInt", "nextLong", "nextULong") && args.size == 1 -> "math.floor(math.random() * ${args[0]})"
            name in setOf("nextUInt", "nextLong", "nextULong") -> inlineOnce(exprs, args) { (lo, hi) -> "(math.floor(math.random() * ($hi - $lo)) + $lo)" }
            name == "nextUBytes" -> "(function(n) local t = {} for i = 1, n do t[i] = math.random(0, 255) end return t end)(${args[0]})"
            else -> error("Unsupported Random call $name")
        }
    }

    private fun isFloatRange(expr: RangeExpr): Boolean =
        types.inferExprType(expr.start) == TypeName.DOUBLE || types.inferExprType(expr.end) == TypeName.DOUBLE

    /** An integer range is the Lua list of its values; a floating-point one only has its two bounds. */
    private fun emitRangeExpr(expr: RangeExpr): String {
        if (isFloatRange(expr)) return "{start = ${emitExpression(expr.start)}, endInclusive = ${emitExpression(expr.end)}}"
        val isChar = types.inferExprType(expr.start) == TypeName.CHAR
        val endStr = if (isChar) "string.byte(${emitExpression(expr.end)})" else emitExpression(expr.end)
        val limit = if (expr.inclusive) endStr else "($endStr - 1)"
        val step = when {
            expr.step != null && expr.descending -> ", -(${emitExpression(expr.step)})"
            expr.step != null -> ", ${emitExpression(expr.step)}"
            expr.descending -> ", -1"
            else -> ""
        }
        if (isChar) {
            return "(function() local __r = {} for __i = string.byte(${emitExpression(expr.start)}), $limit$step do __r[#__r + 1] = string.char(__i) end return __r end)()"
        }
        return "(function() local __r = {} for __i = ${emitExpression(expr.start)}, $limit$step do __r[#__r + 1] = __i end return __r end)()"
    }

    /** A range written as a call: `1.rangeTo(5)`, `1.until(5)`, `5.downTo(1)`, `(1..10).step(2)`. */
    private fun asRange(e: Expression): RangeExpr? = when (e) {
        is RangeExpr -> e
        is ParenExpr -> asRange(e.expr)
        is MethodCallExpr -> if (e.args.size != 1 || hasDeclaredMethod(e)) null else when {
            e.methodName in setOf("rangeTo", "rangeUntil", "until", "downTo") && receiverInstanceClass(e.obj) == null ->
                RangeExpr(e.obj, e.args[0].toExpression(), inclusive = e.methodName == "rangeTo" || e.methodName == "downTo", descending = e.methodName == "downTo")
            e.methodName == "step" -> asRange(e.obj)?.copy(step = e.args[0].toExpression())
            else -> null
        }
        else -> null
    }

    /** `x in a..b` compares against the bounds instead of building the range as a list. */
    private fun inRangeOrContains(expr: BinaryExpr, leftStr: String, rightStr: String): String {
        val range = (expr.right as? RangeExpr)?.takeIf { !it.descending && it.step == null }
            ?: return emitContains(expr.right, rightStr, leftStr, expr.left)
        val upper = if (range.inclusive) "<=" else "<"
        return inlineOnce(listOf(expr.left), listOf(leftStr)) { (x) ->
            "($x >= ${emitExpression(range.start)} and $x $upper ${emitExpression(range.end)})"
        }
    }

    /** An inline Lua expression built from [build], which may use its argument several times: a
     * plain identifier/literal is repeated as-is, anything else is passed through a one-off
     * function so it is only evaluated once. No runtime helper is involved either way. */
    private fun inlineOnce(exprs: List<Expression>, strs: List<String>, build: (List<String>) -> String): String {
        if (exprs.all { it is Identifier || it is IntLiteral || it is DoubleLiteral }) return build(strs)
        val params = listOf("a", "b").take(strs.size)
        return "(function(${params.joinToString(", ")}) return ${build(params)} end)(${strs.joinToString(", ")})"
    }

    /** `kotlin.math`'s top-level functions: Lua's own `math.*` where it has the function, otherwise
     * an inline expression from several of them; null when [name] isn't one (or has another arity). */
    private fun emitMathCall(name: String, args: List<String>, exprs: List<Expression>): String? {
        val one = args.size == 1
        val two = args.size == 2
        return when {
            one && name in setOf("sqrt", "abs", "floor", "ceil", "sin", "cos", "tan", "exp", "asin", "acos", "atan", "sinh", "cosh", "tanh", "log10") ->
                "math.$name(${args[0]})"
            one && name == "ln" -> "math.log(${args[0]})"
            two && name in setOf("max", "min", "atan2") -> "math.$name(${args[0]}, ${args[1]})"
            two && name == "pow" -> "(${args[0]} ^ ${args[1]})"
            one && name == "log2" -> "(math.log(${args[0]}) / math.log(2))"
            two && name == "log" -> "(math.log(${args[0]}) / math.log(${args[1]}))"
            one && name == "asinh" -> inlineOnce(exprs, args) { (x) -> "((${x} < 0 and -1 or 1) * math.log(math.abs($x) + math.sqrt($x * $x + 1)))" }
            one && name == "acosh" -> inlineOnce(exprs, args) { (x) -> "math.log($x + math.sqrt($x * $x - 1))" }
            one && name == "atanh" -> inlineOnce(exprs, args) { (x) -> "(math.log((1 + $x) / (1 - $x)) / 2)" }
            one && name == "sign" -> inlineOnce(exprs, args) { (x) -> "($x > 0 and 1 or ($x < 0 and -1 or $x))" }
            one && name == "truncate" -> inlineOnce(exprs, args) { (x) -> "($x >= 0 and math.floor($x) or math.ceil($x))" }
            one && name == "round" -> "((${args[0]} + 6755399441055744) - 6755399441055744)"
            two && name == "hypot" -> inlineOnce(exprs, args) { (x, y) -> "math.sqrt($x * $x + $y * $y)" }
            one && name in setOf("cbrt", "expm1", "ln1p") -> {
                useRuntimeHelper(RuntimeHelper.MATH_PRECISE)
                "__$name(${args[0]})"
            }
            else -> null
        }
    }

    /** `kotlin.math`'s extension functions on Double/Int (`x.roundToInt()`, `x.nextUp()`, ...). */
    private fun emitMathExtensionCall(expr: MethodCallExpr): String? {
        val args = expr.args.map { emitExpression(it.toExpression()) }
        val receiver = emitExpression(expr.obj)
        return when {
            expr.methodName in setOf("roundToInt", "roundToLong") && args.isEmpty() -> "math.floor($receiver + 0.5)"
            expr.methodName in setOf("nextUp", "nextDown") && args.isEmpty() -> {
                useRuntimeHelper(RuntimeHelper.MATH_FLOAT)
                "__${expr.methodName}($receiver)"
            }
            expr.methodName == "nextTowards" && args.size == 1 -> {
                useRuntimeHelper(RuntimeHelper.MATH_FLOAT)
                "__nextTowards($receiver, ${args[0]})"
            }
            expr.methodName == "withSign" && args.size == 1 ->
                inlineOnce(listOf(expr.obj, expr.args[0].toExpression()), listOf(receiver, args[0])) { (x, sign) ->
                    "(($sign < 0 or 1 / $sign < 0) and -math.abs($x) or math.abs($x))"
                }
            expr.methodName == "toChar" && args.isEmpty() -> "string.char($receiver)"
            expr.methodName == "coerceAtLeast" && args.size == 1 -> "math.max($receiver, ${args[0]})"
            expr.methodName == "coerceAtMost" && args.size == 1 -> "math.min($receiver, ${args[0]})"
            expr.methodName == "coerceIn" && args.size == 2 -> "math.min(math.max($receiver, ${args[0]}), ${args[1]})"
            expr.methodName == "coerceIn" && args.size == 1 && (expr.args[0].toExpression() as? RangeExpr)?.let { it.inclusive && !it.descending && it.step == null } == true -> {
                val range = expr.args[0].toExpression() as RangeExpr
                "math.min(math.max($receiver, ${emitExpression(range.start)}), ${emitExpression(range.end)})"
            }
            expr.methodName == "IEEErem" && args.size == 1 -> {
                useRuntimeHelper(RuntimeHelper.MATH_PRECISE)
                "__ieeerem($receiver, ${args[0]})"
            }
            else -> null
        }
    }

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

    override fun emitArgList(args: List<CallArg>): String {
        if (args.isNotEmpty() && args.all { it is NamedArg }) {
            return emitNamedArgTable(args.filterIsInstance<NamedArg>())
        }
        return args.joinToString(", ") { if (it is SpreadArg) "unpack(${emitExpression(it.expr)})" else emitExpression(it.toExpression()) }
    }

    private fun recordArguments(type: String, args: List<CallArg>): List<NamedArg> {
        val fields = bindings.recordFields(type)!!
        val values = args.mapIndexed { index, arg ->
            when (arg) {
                is NamedArg -> arg.also { require(it.name in fields) { "Unknown record field ${it.name} for $type" } }
                is PositionalArg -> NamedArg(fields.getOrElse(index) { error("Too many record arguments for $type") }, arg.expr)
                else -> error("Spread arguments are not supported for API records")
            }
        }
        require(values.map { it.name }.distinct().size == values.size) { "Duplicate record arguments for $type" }
        return values
    }

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
                is TemplateExprPart.Expr -> formatForToString(part.expr, emitExpression(part.expr))
            }
        }
        return if (fragments.size == 1) fragments[0] else fragments.joinToString(" .. ")
    }

    private fun emitCollectionToStringExpr(className: String, receiverStr: String): String {
        useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        useRuntimeHelper(RuntimeHelper.FMT_VALUE)
        return when (className) {
            "Set" -> "(\"[\" .. table.concat((function() local __p = {} for _, __v in ipairs($receiverStr.list) do table.insert(__p, __fmt(__v)) end return __p end)(), \", \") .. \"]\")"
            "Map" -> "(\"{\" .. table.concat((function() local __r = $receiverStr local __p = {} for _, __k in ipairs(__r.keys) do table.insert(__p, __fmt(__k) .. \"=\" .. __fmt(__r.map[__k])) end return __p end)(), \", \") .. \"}\")"
            else -> "(\"[\" .. table.concat((function() local __p = {} for _, __v in ipairs($receiverStr) do table.insert(__p, __fmt(__v)) end return __p end)(), \", \") .. \"]\")"
        }
    }

    private fun formatForToString(expr: Expression, exprStr: String): String {
        val className = receiverInstanceClass(expr)
        if (className != null && className in setOf("List", "Set", "Map")) return emitCollectionToStringExpr(className, exprStr)
        if (className == "StringBuilder") return "table.concat($exprStr.buf)"
        if (isPairValued(expr)) return pairFormatted(exprStr)
        return if (isDoubleTyped(expr)) doubleFormatted(exprStr) else "tostring($exprStr)"
    }

    private fun collectionAwareOrPlain(expr: Expression, exprStr: String): String {
        val className = receiverInstanceClass(expr)
        if (className != null && className in setOf("List", "Set", "Map")) return emitCollectionToStringExpr(className, exprStr)
        if (className == "StringBuilder") return "table.concat($exprStr.buf)"
        if (isPairValued(expr)) return pairFormatted(exprStr)
        return if (isDoubleTyped(expr)) doubleFormatted(exprStr) else exprStr
    }

    private fun isPairValued(expr: Expression): Boolean = when (expr) {
        is ToPairExpr -> true
        is ParenExpr -> isPairValued(expr.expr)
        is MethodCallExpr -> expr.methodName == "partition" || receiverInstanceClass(expr) == "Pair"
        is CallExpr -> expr.callee == "Pair"
        else -> receiverInstanceClass(expr) == "Pair"
    }

    private fun pairFormatted(exprStr: String): String {
        useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
        useRuntimeHelper(RuntimeHelper.FMT_VALUE)
        return "__fmt($exprStr)"
    }

    private fun isDoubleTyped(expr: Expression): Boolean = types.inferExprType(expr) == TypeName.DOUBLE

    private fun doubleFormatted(exprStr: String): String {
        useRuntimeHelper(RuntimeHelper.FMT_DOUBLE)
        return "__fmtDouble($exprStr)"
    }
}
