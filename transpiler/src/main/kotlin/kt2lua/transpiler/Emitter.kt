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

private val SEQUENCE_CHAIN_METHODS = setOf(
    "map", "filter", "take", "drop", "takeWhile", "dropWhile", "onEach", "mapNotNull", "filterNotNull", "distinct",
)

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
        expr.methodName == "asSequence" -> "Sequence"
        receiverClass == "Sequence" && expr.methodName in SEQUENCE_CHAIN_METHODS -> "Sequence"
        receiverClass == "Sequence" && expr.methodName in setOf("toList", "toMutableList") -> "List"
        receiverClass == "Sequence" && expr.methodName == "toSet" -> "Set"
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
        types.registerExternalClass("Sequence", ClassInfo(emptyList(), emptySet(), false, native = true))
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

    private fun emitTopLevelVariableDecl(decl: VariableDecl, moduleVar: String) {
        emitVariableDecl(decl)
        if (!decl.isPrivate) line("$moduleVar.${decl.name} = ${decl.name}")
    }

    private fun isLengthProperty(name: String): Boolean = name == "size" || name == "length"

    private fun lengthAccess(receiverStr: String, className: String?): String = when (className) {
        "Set" -> "#$receiverStr.list"
        "Map" -> "#$receiverStr.keys"
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

    private val NUMERIC_CAST_NAMES = setOf("toDouble", "toInt", "toFloat", "toLong", "toShort", "toByte")

    private fun emitMethodCallTarget(expr: MethodCallExpr): String {
        if (expr.args.isEmpty() && expr.methodName in NUMERIC_CAST_NAMES) return emitExpression(expr.obj)
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
            return if (expr.methodName == "last" || expr.methodName == "lastOrNull") {
                "(type($receiverStr) == \"string\" and string.sub($receiverStr, -1, -1) or $receiverStr[#$receiverStr])"
            } else {
                "(type($receiverStr) == \"string\" and string.sub($receiverStr, 1, 1) or $receiverStr[1])"
            }
        }
        if (expr.methodName == "joinToString" && collClass != "Map" && collClass != "Sequence" && !isModuleCall(expr.obj)) {
            return emitJoinToStringExpr(iterationArrayOf(expr.obj), expr.args)
        }
        if (collClass == "Set") {
            val receiverStr = emitExpression(expr.obj)
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
            if (expr.methodName == "isEmpty" && expr.args.isEmpty()) return "(#$receiverStr.keys == 0)"
            if (expr.methodName == "isNotEmpty" && expr.args.isEmpty()) return "(#$receiverStr.keys ~= 0)"
            if (expr.methodName in mutationMethodNames.getValue("Map")) {
                emitCollectionIIFE(null) { emitInlineMutation(expr) }?.let { return it }
            }
        }
        if (expr.methodName == "asSequence" && expr.args.isEmpty() && collClass in setOf("List", "Set")) {
            return emitListToSequenceExpr(expr.obj)
        }
        if (collClass == "Sequence") {
            val seqStr = emitExpression(expr.obj)
            val arg0 = expr.args.getOrNull(0)?.toExpression()
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
                expr.methodName == "first" && expr.args.isEmpty() -> return "(($seqStr)())()"
                expr.methodName == "firstOrNull" && expr.args.isEmpty() -> return "(($seqStr)())()"
                expr.methodName in setOf("find", "first", "firstOrNull") && arg0 != null ->
                    return emitSequenceTerminalExpr { emitSequenceFindStatements(it, seqStr, arg0) }
                expr.methodName in setOf("toList", "toMutableList") && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceToListStatements(it, seqStr) }
                expr.methodName == "toSet" && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceToSetStatements(it, seqStr) }
                expr.methodName == "count" && expr.args.isEmpty() ->
                    return emitSequenceTerminalExpr { emitSequenceCountStatements(it, seqStr) }
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
        if (collClass == "List") {
            val receiverStr = emitExpression(expr.obj)
            if (expr.methodName == "get" && expr.args.size == 1) return "$receiverStr[(${emitExpression(expr.args[0].toExpression())}) + 1]"
            if (expr.methodName == "removeAt" && expr.args.size == 1) {
                return "table.remove($receiverStr, (${emitExpression(expr.args[0].toExpression())}) + 1)"
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

    private fun instanceClassOfExpr(expr: Expression): String? = when (expr) {
        is StringTemplateLiteral -> "String"
        is Identifier -> {
            val resolvedName = if (expr.name == "self") types.currentReceiver() ?: "self" else expr.name
            types.instanceClassOf(resolvedName) ?: bindings.global(expr.name)
                ?: types.memberReceiver(expr.name)?.let { receiver ->
                    types.instanceClassOf(receiver)?.let { types.classInfo(it)?.propertyTypes?.get(expr.name) }
                }
        }
        is PropertyAccessExpr -> expressionPath(expr)?.let { bindings.global(it) }
            ?: receiverInstanceClass(expr.obj)?.let { receiver ->
                if (types.classInfo(receiver)?.native == true) bindings.property(receiver, expr.name)
                else types.classInfo(receiver)?.propertyTypes?.get(expr.name)
            }
        is ParenExpr -> instanceClassOfExpr(expr.expr)
        is NotNullAssertExpr -> instanceClassOfExpr(expr.expr)
        is CallExpr -> operationRegistry.resolveTopLevel(expr.callee, OperationCall(null, expr.args), this)?.resultKind(null)
            ?: if (expr.callee == "sequenceOf" || (expr.callee == "generateSequence" && expr.args.size == 2)) "Sequence"
            else if (expr.callee == "runCatching" && types.memberReceiver(expr.callee) == null) RESULT_CLASS
            else if (expr.callee == "run" && expr.args.size == 1 && types.memberReceiver(expr.callee) == null)
                scopeResultClass(expr.args.single().toExpression(), null, noParams = true)
            else if (expr.callee == "with" && expr.args.size == 2 && types.memberReceiver(expr.callee) == null)
                scopeResultClass(expr.args[1].toExpression(), receiverInstanceClass(expr.args[0].toExpression()), receiverStyle = true)
            else if (types.classInfo(expr.callee) != null) expr.callee
            else types.memberReceiver(expr.callee)?.let { receiver ->
                types.instanceClassOf(receiver)?.let { types.methodReturnClass(it, expr.callee) }
            } ?: types.functionReturnClass(expr.callee)
        is MethodCallExpr -> if (expr.methodName == "copy" && receiverInstanceClass(expr.obj)?.let { bindings.isDataRecord(it) } == true) {
            receiverInstanceClass(expr.obj)
        } else if (expr.methodName in setOf("also", "apply", "takeIf", "takeUnless") && !hasDeclaredMethod(expr)) {
            receiverInstanceClass(expr.obj)
        } else if (expr.methodName in setOf("let", "run") && expr.args.size == 1 && !hasDeclaredMethod(expr)) {
            scopeResultClass(expr.args.single().toExpression(), receiverInstanceClass(expr.obj), receiverStyle = expr.methodName == "run")
        } else {
            if (expr.methodName == "new" && expressionPath(expr.obj)?.let { nativeFactory(it) } != null) {
                bindings.type(expressionPath(expr.obj)!!)
            } else receiverInstanceClass(expr.obj)?.let { receiverClass ->
                if (receiverClass == RESULT_CLASS && expr.methodName in setOf("onSuccess", "onFailure")) RESULT_CLASS
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
            receiverInstanceClass(expr.left)?.takeIf { it == "List" || it == "Set" }
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
            usedRuntimeHelpers += RuntimeHelper.INT_DIV
            line("$targetStr = __intdiv($targetStr, $rhsStr)")
        } else {
            line("$targetStr = $targetStr $binOp ($rhsStr)")
        }
    }

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
        is BooleanLiteral -> expr.value.toString()
        NullLiteral -> "nil"
        is Identifier -> emitIdentifier(expr.name)
        is ParenExpr -> "(${emitExpression(expr.expr)})"
        is UnaryExpr -> if (expr.op == "!") "(not ${emitExpression(expr.expr)})" else "(-${emitExpression(expr.expr)})"
        is StringTemplateLiteral -> emitTemplate(expr)
        is BinaryExpr -> emitBinary(expr)
        is CallExpr -> emitCall(expr)
        is PropertyAccessExpr -> receiverInstanceClass(expr.obj).let { cls ->
            if (isLengthProperty(expr.name) && (cls == null || cls in setOf("String", "List", "Set", "Map"))) lengthAccess(emitExpression(expr.obj), cls)
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
    }

    private fun emitPropertyAccess(receiver: Expression, name: String): String {
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
            line("local __arr = {${args.joinToString(", ") { emitExpression(it.toExpression()) }}}")
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
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
        },
        produce = {
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("return __fn(__v)")
        },
    )

    private fun emitSequenceFilterExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("if __fn(__v) then return __v end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceMapNotNullExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("local __r = __fn(__v)")
            line("if __r ~= nil then return __r end")
            popIndent()
            line("end")
        },
    )

    private fun emitSequenceFilterNotNullExpr(upstream: Expression): String = emitSequenceExpr(
        setup = { emitSequenceUpstream(upstream) },
        produce = { line("return __it()") },
    )

    private fun emitSequenceOnEachExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
        },
        produce = {
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("__fn(__v)")
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
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
            line("local __done = false")
        },
        produce = {
            line("if __done then return nil end")
            line("local __v = __it()")
            line("if __v == nil or not __fn(__v) then __done = true; return nil end")
            line("return __v")
        },
    )

    private fun emitSequenceDropWhileExpr(upstream: Expression, callback: Expression): String = emitSequenceExpr(
        setup = {
            emitSequenceUpstream(upstream)
            line("local __fn = ${if (callback is LambdaExpr) emitLambda(callback) else emitExpression(callback)}")
            line("local __dropping = true")
        },
        produce = {
            line("while true do")
            pushIndent()
            line("local __v = __it()")
            line("if __v == nil then return nil end")
            line("if not __dropping then return __v end")
            line("if not __fn(__v) then __dropping = false; return __v end")
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
            line("if not __seen[__v] then __seen[__v] = true; return __v end")
            popIndent()
            line("end")
        },
    )

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
            line("for $paramName in ($seqStr)() do")
            pushIndent()
            callback.body.body.forEach { emitStatement(it) }
            popIndent()
            line("end")
            types.popScope()
        } else {
            val fnVar = nextTemp("__fn")
            line("local $fnVar = ${emitExpression(callback)}")
            line("for __v in ($seqStr)() do $fnVar(__v) end")
        }
    }

    private fun emitSequenceFindStatements(resultVar: String, seqStr: String, callback: Expression) {
        line("local $resultVar = nil")
        if (callback is LambdaExpr) {
            val paramName = callback.params.firstOrNull() ?: "it"
            types.pushScope()
            types.env.declare(paramName, TypeName.ANY)
            line("for $paramName in ($seqStr)() do")
            pushIndent()
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
            line("for __v in ($seqStr)() do if $fnVar(__v) then $resultVar = __v; break end end")
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
            line("for $paramName in ($seqStr)() do")
            pushIndent()
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
            line(sink("$fnVar(__v)"))
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
            line("for $xParam in ($seqStr)() do")
            pushIndent()
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
            line("for __v in ($seqStr)() do $resultVar = $fnVar($resultVar, __v) end")
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

    private fun emitBinary(expr: BinaryExpr): String {
        val leftStr = emitExpression(expr.left)
        val rightStr = emitExpression(expr.right)
        return when (expr.op) {
            "&&" -> "($leftStr and $rightStr)"
            "||" -> "($leftStr or $rightStr)"
            "==" -> if (receiverInstanceClass(expr.left) in setOf("List", "Set", "Map")) {
                emitCollectionEquals(expr.left, leftStr, rightStr)
            } else {
                "($leftStr == $rightStr)"
            }
            "!=" -> if (receiverInstanceClass(expr.left) in setOf("List", "Set", "Map")) {
                "(not ${emitCollectionEquals(expr.left, leftStr, rightStr)})"
            } else {
                "($leftStr ~= $rightStr)"
            }
            "in" -> emitContains(expr.right, rightStr, leftStr, expr.left)
            "!in" -> "(not ${emitContains(expr.right, rightStr, leftStr, expr.left)})"
            "+" -> when (receiverInstanceClass(expr.left)) {
                "List" -> emitListPlusExpr(leftStr, expr.right, rightStr)
                "Set" -> emitSetPlusMinusExpr(leftStr, expr.right, rightStr, removing = false)
                else -> "($leftStr + $rightStr)"
            }
            "-" -> when (receiverInstanceClass(expr.left)) {
                "List" -> emitListMinusExpr(leftStr, expr.right, rightStr)
                "Set" -> emitSetPlusMinusExpr(leftStr, expr.right, rightStr, removing = true)
                else -> "($leftStr - $rightStr)"
            }
            "/" -> {
                val lt = types.inferExprType(expr.left)
                val rt = types.inferExprType(expr.right)
                if (lt == TypeName.INT && rt == TypeName.INT) {
                    usedRuntimeHelpers += RuntimeHelper.INT_DIV
                    "__intdiv($leftStr, $rightStr)"
                } else {
                    "($leftStr / $rightStr)"
                }
            }
            else -> "($leftStr ${expr.op} $rightStr)"
        }
    }

    private fun emitCall(expr: CallExpr): String {
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
                "{${expr.args.joinToString(", ") { emitExpression(it.toExpression()) }}}"
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
        if (expr.callee == "generateSequence" && expr.args.size == 2) {
            return emitGenerateSequenceExpr(expr.args[0].toExpression(), expr.args[1].toExpression())
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
        return when (expr.callee) {
            "println" -> "print(${expr.args.mapIndexed { i, arg -> collectionAwareOrPlain(arg.toExpression(), positional[i]) }.joinToString(", ")})"
            "print" -> "io.write(${if (expr.args.isEmpty()) "\"\"" else formatForToString(expr.args[0].toExpression(), positional[0])})"
            "sqrt" -> "math.sqrt(${positional[0]})"
            "abs" -> "math.abs(${positional[0]})"
            "pow" -> "(${positional[0]} ^ ${positional[1]})"
            "max" -> "math.max(${positional[0]}, ${positional[1]})"
            "min" -> "math.min(${positional[0]}, ${positional[1]})"
            else -> "${expr.callee}(${emitArgList(expr.args)})"
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

    private fun emitCollectionToStringExpr(className: String, receiverStr: String): String = when (className) {
        "Set" -> "(\"[\" .. table.concat((function() local __p = {} for _, __v in ipairs($receiverStr.list) do table.insert(__p, tostring(__v)) end return __p end)(), \", \") .. \"]\")"
        "Map" -> "(\"{\" .. table.concat((function() local __r = $receiverStr local __p = {} for _, __k in ipairs(__r.keys) do table.insert(__p, tostring(__k) .. \"=\" .. tostring(__r.map[__k])) end return __p end)(), \", \") .. \"}\")"
        else -> "(\"[\" .. table.concat((function() local __p = {} for _, __v in ipairs($receiverStr) do table.insert(__p, tostring(__v)) end return __p end)(), \", \") .. \"]\")"
    }

    private fun formatForToString(expr: Expression, exprStr: String): String {
        val className = receiverInstanceClass(expr)
        return if (className != null && className in setOf("List", "Set", "Map")) emitCollectionToStringExpr(className, exprStr) else "tostring($exprStr)"
    }

    private fun collectionAwareOrPlain(expr: Expression, exprStr: String): String {
        val className = receiverInstanceClass(expr)
        return if (className != null && className in setOf("List", "Set", "Map")) emitCollectionToStringExpr(className, exprStr) else exprStr
    }
}
