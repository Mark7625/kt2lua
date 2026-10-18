package kt2lua.transpiler

/** [name] is also the `require(...)` path other files in the same [transpileProject] call use
 * to load it, and the output map's key. */
data class ProjectFile(val name: String, val source: String)

data class ProjectTranspileResult(val files: Map<String, String>)

/** Transpiles several files together, auto-detecting cross-file references and inserting the
 * `require(...)` calls they need. Only auto-requires when a free identifier exactly matches
 * another file's top-level, non-private declaration name; a name declared in more than one file
 * is ambiguous and never auto-required for either. */
fun transpileProject(files: List<ProjectFile>, style: EmitStyle = EmitStyle.DEFAULT, literalConstants: Map<String, String> = emptyMap(), bindings: LuaBindings = LuaBindings()): ProjectTranspileResult {
    require(files.map { it.name }.distinct().size == files.size) { "Duplicate Lua module paths in project inputs" }
    val fileBindings = files.associate { it.name to bindings.forSource(it.source) }
    val parsed = files.map { it.name to Parser(Lexer(it.source).tokenize(), literalConstants, fileBindings.getValue(it.name)).parseProgram() }
    val globalClassInfo = classInfoAcrossFiles(parsed.map { it.second })

    val owner = mutableMapOf<String, String>()
    val ambiguous = mutableSetOf<String>()
    for ((fileName, program) in parsed) {
        for (stmt in program.body) {
            val declared = topLevelDeclName(stmt) ?: continue
            val existing = owner[declared]
            if (existing != null && existing != fileName) ambiguous.add(declared) else owner[declared] = fileName
        }
    }
    for (name in ambiguous) owner.remove(name)

    val results = mutableMapOf<String, String>()
    for ((fileName, program) in parsed) {
        val ownDeclarations = program.body.mapNotNull { topLevelDeclName(it) }.toSet()
        fun neededFilesFor(names: Set<String>) = names
            .filter { it !in ownDeclarations }
            .mapNotNull { owner[it] }
            .filter { it != fileName }
            .toSortedSet()

        // A top-level statement other than `main()` (e.g. a class's `: Super(...)` heritage
        // clause) runs at module-load time, before `main()` is ever called, so its free names
        // need `require(...)` injected at the top of the file rather than deferred into `main()`.
        val mainIndex = program.body.indexOfFirst { it is FunctionDecl && it.name == "main" }
        val immediateNames = mutableSetOf<String>()
        for ((i, stmt) in program.body.withIndex()) if (i != mainIndex) collectFreeNames(stmt, immediateNames)
        val immediateNeeded = neededFilesFor(immediateNames)

        val deferredNames = mutableSetOf<String>()
        if (mainIndex != -1) collectFreeNames(program.body[mainIndex], deferredNames)
        val deferredNeeded = neededFilesFor(deferredNames) - immediateNeeded

        var result = program
        if (immediateNeeded.isNotEmpty()) result = prependRequires(result, immediateNeeded)
        if (deferredNeeded.isNotEmpty()) result = injectRequiresIntoMain(result, deferredNeeded)
        results[fileName] = Emitter(style, fileBindings.getValue(fileName)).emitProgram(result, globalClassInfo)
    }
    return ProjectTranspileResult(results)
}

/** Redoes [TypeAnalyzer.collectSignatures]'s `ClassDecl` case across every file's top-level
 * declarations at once, in file-list order, so a subclass's inherited methods resolve for
 * colon-calls even when its superclass lives in a sibling file. A superclass listed *after* its
 * subclass in the file list isn't resolved — same order-dependent trade-off [collectSignatures]
 * already accepts within one file. */
private fun classInfoAcrossFiles(programs: List<Program>): Map<String, ClassInfo> {
    val classes = mutableMapOf<String, ClassInfo>()
    val returnTypes = TypeAnalyzer()
    for (program in programs) {
        for (stmt in program.body) {
            if (stmt !is ClassDecl) continue
            val superInfo = stmt.superClass?.let { classes[it] }
            classes[stmt.name] = ClassInfo(
                paramOrder = stmt.params.map { it.name },
                methodNames = stmt.methods.map { it.name }.toSet() + (superInfo?.methodNames ?: emptySet()),
                hasToString = stmt.methods.any { it.name == "toString" } || superInfo?.hasToString == true,
                methodReturnTypes = (superInfo?.methodReturnTypes ?: emptyMap()) +
                    stmt.methods.associate { it.name to returnTypes.rawReturnTypeOf(it) },
                propertyTypes = (superInfo?.propertyTypes ?: emptyMap()) +
                    stmt.params.associate { it.name to "" } +
                    stmt.properties.associate { it.name to it.rawType },
                superClass = stmt.superClass,
            )
        }
    }
    return classes
}

private fun topLevelDeclName(stmt: Statement): String? = when (stmt) {
    is ObjectDecl -> stmt.name
    is ClassDecl -> stmt.name
    is FunctionDecl -> if (!stmt.isPrivate) stmt.name else null
    is VariableDecl -> if (!stmt.isPrivate) stmt.name else null
    else -> null
}

private fun requireCallStatement(moduleName: String): Statement =
    ExpressionStatement(CallExpr("require", listOf(PositionalArg(StringTemplateLiteral(listOf(TemplateExprPart.Text(moduleName)))))))

private fun prependRequires(program: Program, neededFiles: Collection<String>): Program =
    Program(neededFiles.map { requireCallStatement(it) } + program.body)

private fun injectRequiresIntoMain(program: Program, neededFiles: Collection<String>): Program {
    val requireStatements = neededFiles.map { requireCallStatement(it) }
    val mainIndex = program.body.indexOfFirst { it is FunctionDecl && it.name == "main" }
    val main = program.body[mainIndex] as FunctionDecl
    val newMain = when (val body = main.body) {
        is BlockBody -> main.copy(body = BlockBody(Block(requireStatements + body.block.body)))
        is ExprBody -> main // an expression-bodied `main()` isn't valid Kotlin anyway; leave as-is
    }
    return Program(program.body.toMutableList().also { it[mainIndex] = newMain })
}

private fun collectFreeNames(stmt: Statement, out: MutableSet<String>) {
    when (stmt) {
        is ImportStatement -> {}
        is ObjectDecl -> for (s in stmt.body) collectFreeNames(s, out)
        is ClassDecl -> {
            stmt.superClass?.let { out.add(it) }
            for (arg in stmt.superArgs) collectFreeNames(arg.toExpression(), out)
            for (p in stmt.properties) collectFreeNames(p, out)
            for (m in stmt.methods) collectFreeNames(m, out)
        }
        is FunctionDecl -> collectFreeNames(stmt.body, out)
        is VariableDecl -> collectFreeNames(stmt.init, out)
        is ExpressionStatement -> collectFreeNames(stmt.expr, out)
        is ReturnStatement -> stmt.expr?.let { collectFreeNames(it, out) }
        is IfStatement -> {
            collectFreeNames(stmt.cond, out)
            collectFreeNames(stmt.thenBranch, out)
            stmt.elseBranch?.let { collectFreeNames(it, out) }
        }
        is ForStatement -> {
            collectFreeNames(stmt.start, out)
            collectFreeNames(stmt.end, out)
            stmt.step?.let { collectFreeNames(it, out) }
            collectFreeNames(stmt.body, out)
        }
        is ForInStatement -> {
            collectFreeNames(stmt.iterable, out)
            collectFreeNames(stmt.body, out)
        }
        is WhileStatement -> {
            collectFreeNames(stmt.cond, out)
            collectFreeNames(stmt.body, out)
        }
        is Assignment -> {
            collectFreeNames(stmt.target, out)
            collectFreeNames(stmt.expr, out)
        }
        is Block -> for (s in stmt.body) collectFreeNames(s, out)
        is ThrowStatement -> collectFreeNames(stmt.expr, out)
        is TryStatement -> {
            collectFreeNames(stmt.tryBlock, out)
            stmt.catch?.let { for (s in it.body.body) collectFreeNames(s, out) }
            stmt.finallyBlock?.let { collectFreeNames(it, out) }
        }
        is WhenStatement -> {
            stmt.subject?.let { collectFreeNames(it, out) }
            for (branch in stmt.branches) {
                for (cond in branch.conditions) collectFreeNames(cond, out)
                collectFreeNames(branch.body, out)
            }
            stmt.elseBranch?.let { collectFreeNames(it, out) }
        }
    }
}

private fun collectFreeNames(body: FunctionBody, out: MutableSet<String>) {
    when (body) {
        is ExprBody -> collectFreeNames(body.expr, out)
        is BlockBody -> collectFreeNames(body.block, out)
    }
}

private fun collectFreeNames(target: AssignTarget, out: MutableSet<String>) {
    when (target) {
        is NameTarget -> out.add(target.name)
        is PropertyTarget -> collectFreeNames(target.obj, out)
        is IndexTarget -> {
            collectFreeNames(target.obj, out)
            collectFreeNames(target.index, out)
        }
    }
}

private fun collectFreeNames(cond: WhenCondition, out: MutableSet<String>) {
    when (cond) {
        is ValueCondition -> collectFreeNames(cond.expr, out)
        is InRangeCondition -> {
            collectFreeNames(cond.start, out)
            collectFreeNames(cond.end, out)
        }
        is IsTypeCondition -> {}
    }
}

private fun collectFreeNames(expr: Expression, out: MutableSet<String>) {
    when (expr) {
        is IntLiteral, is DoubleLiteral, is BooleanLiteral, is NullLiteral -> {}
        is StringTemplateLiteral -> for (part in expr.parts) when (part) {
            is TemplateExprPart.Text -> {}
            is TemplateExprPart.Expr -> collectFreeNames(part.expr, out)
        }
        is Identifier -> out.add(expr.name)
        is BinaryExpr -> {
            collectFreeNames(expr.left, out)
            collectFreeNames(expr.right, out)
        }
        is UnaryExpr -> collectFreeNames(expr.expr, out)
        is CallExpr -> {
            out.add(expr.callee)
            for (arg in expr.args) collectFreeNames(arg.toExpression(), out)
        }
        is PropertyAccessExpr -> collectFreeNames(expr.obj, out)
        is MethodCallExpr -> {
            collectFreeNames(expr.obj, out)
            for (arg in expr.args) collectFreeNames(arg.toExpression(), out)
        }
        is FunctionRef -> out.add(expr.name)
        is IfExpr -> {
            collectFreeNames(expr.cond, out)
            collectFreeNames(expr.thenExpr, out)
            collectFreeNames(expr.elseExpr, out)
        }
        is ParenExpr -> collectFreeNames(expr.expr, out)
        is SafeChainExpr -> {
            collectFreeNames(expr.base, out)
            for (step in expr.steps) when (step) {
                is ChainProp -> {}
                is ChainCall -> for (arg in step.args) collectFreeNames(arg.toExpression(), out)
            }
        }
        is ElvisExpr -> {
            collectFreeNames(expr.left, out)
            collectFreeNames(expr.right, out)
        }
        is ReturnExpr -> expr.value?.let { collectFreeNames(it, out) }
        is NotNullAssertExpr -> collectFreeNames(expr.expr, out)
        is LambdaExpr -> for (s in expr.body.body) collectFreeNames(s, out)
        is IndexExpr -> {
            collectFreeNames(expr.obj, out)
            collectFreeNames(expr.index, out)
        }
        is ToPairExpr -> {
            collectFreeNames(expr.first, out)
            collectFreeNames(expr.second, out)
        }
        is WhenExpr -> {
            expr.subject?.let { collectFreeNames(it, out) }
            for (branch in expr.branches) {
                for (cond in branch.conditions) collectFreeNames(cond, out)
                collectFreeNames(branch.result, out)
            }
            collectFreeNames(expr.elseResult, out)
        }
    }
}
