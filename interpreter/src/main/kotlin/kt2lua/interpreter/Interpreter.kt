package kt2lua.interpreter

/** Resolves a `require(name)` call to Lua source, or returns null if the module can't be found. */
interface ModuleLoader {
    fun load(moduleName: String): String?
}

/** A tree-walking evaluator. [onOutput] receives every byte `print`/`io.write` would send to
 * stdout, so callers can capture it (tests) or stream it to the console. */
class Interpreter(
    private val onOutput: (String) -> Unit = { kotlin.io.print(it) },
    private val moduleLoader: ModuleLoader? = null,
) {
    val globals = Environment()
    private val loadedModules = mutableMapOf<String, List<Any?>>()

    init {
        installStdlib(this, globals, onOutput)
    }

    fun run(chunk: Chunk): List<Any?> = try {
        execBlock(chunk.body, globals)
        emptyList()
    } catch (ret: ReturnSignal) {
        ret.values
    }

    fun require(moduleName: String): List<Any?> {
        loadedModules[moduleName]?.let { return it }
        val loader = moduleLoader ?: throw LuaRuntimeException("module '$moduleName' not found (no module loader configured)")
        val source = loader.load(moduleName) ?: throw LuaRuntimeException("module '$moduleName' not found")
        val chunk = LuaParser(LuaLexer(source).tokenize()).parseChunk()
        val moduleEnv = Environment(globals)
        val result = try {
            execBlock(chunk.body, moduleEnv)
            emptyList()
        } catch (ret: ReturnSignal) {
            ret.values
        }
        loadedModules[moduleName] = result
        return result
    }

    fun callValue(callee: Any?, args: List<Any?>): List<Any?> {
        if (callee is LuaCallable) return callee.call(args)
        if (callee is LuaTable) {
            val callMeta = callee.metatable?.rawGet("__call")
            if (callMeta is LuaCallable) return callMeta.call(listOf(callee) + args)
        }
        throw LuaRuntimeException("attempt to call a ${typeName(callee)} value")
    }

    fun tostring(value: Any?): String = luaToString(value) { fn, a -> callValue(fn, a) }

    internal fun execBlock(stmts: List<Stmt>, env: Environment) {
        for (stmt in stmts) exec(stmt, env)
    }

    private fun exec(stmt: Stmt, env: Environment) {
        when (stmt) {
            is LocalDecl -> {
                val values = evalExprList(stmt.exprs, env)
                stmt.names.forEachIndexed { i, name -> env.define(name, values.getOrNull(i)) }
            }
            is LocalFunctionDecl ->
                env.define(stmt.name, LuaClosure(stmt.params, stmt.isVararg, stmt.body, env, this))
            is Assign -> execAssign(stmt, env)
            is ExprStmt -> evalMulti(stmt.expr, env)
            is Return -> throw ReturnSignal(evalExprList(stmt.exprs, env))
            Break -> throw BreakSignal()
            is Do -> execBlock(stmt.body, Environment(env))
            is If -> {
                if (isTruthy(evalSingle(stmt.cond, env))) {
                    execBlock(stmt.thenBody, Environment(env))
                } else if (stmt.elseBody != null) {
                    execBlock(stmt.elseBody, Environment(env))
                }
            }
            is NumericFor -> execNumericFor(stmt, env)
            is GenericFor -> execGenericFor(stmt, env)
            is While -> try {
                while (isTruthy(evalSingle(stmt.cond, env))) execBlock(stmt.body, Environment(env))
            } catch (b: BreakSignal) {
            }
            is Repeat -> execRepeat(stmt, env)
        }
    }

    private fun execAssign(stmt: Assign, env: Environment) {
        val values = evalExprList(stmt.exprs, env)
        stmt.targets.forEachIndexed { i, target ->
            val value = values.getOrNull(i)
            when (target) {
                is NameTarget -> env.set(target.name, value)
                is IndexTarget -> setIndex(evalSingle(target.obj, env), evalSingle(target.key, env), value)
            }
        }
    }

    private fun execNumericFor(stmt: NumericFor, env: Environment) {
        val start = asNumber(evalSingle(stmt.start, env))
        val limit = asNumber(evalSingle(stmt.limit, env))
        val step = stmt.step?.let { asNumber(evalSingle(it, env)) } ?: 1.0
        var i = start
        try {
            while ((step > 0 && i <= limit) || (step < 0 && i >= limit)) {
                val loopEnv = Environment(env)
                loopEnv.define(stmt.varName, i)
                execBlock(stmt.body, loopEnv)
                i += step
            }
        } catch (b: BreakSignal) {
        }
    }

    /** Implements Lua's generic-for protocol: `for vars in f, s, ctrl do ... end`. */
    private fun execGenericFor(stmt: GenericFor, env: Environment) {
        val initial = evalExprList(stmt.exprs, env)
        val iterator = initial.getOrNull(0)
        val state = initial.getOrNull(1)
        var control = initial.getOrNull(2)
        try {
            while (true) {
                val results = callValue(iterator, listOf(state, control))
                val first = results.getOrNull(0) ?: break
                control = first
                val loopEnv = Environment(env)
                stmt.names.forEachIndexed { i, name -> loopEnv.define(name, results.getOrNull(i)) }
                execBlock(stmt.body, loopEnv)
            }
        } catch (b: BreakSignal) {
        }
    }

    private fun execRepeat(stmt: Repeat, env: Environment) {
        try {
            while (true) {
                val loopEnv = Environment(env)
                execBlock(stmt.body, loopEnv)
                if (isTruthy(evalSingle(stmt.cond, loopEnv))) break
            }
        } catch (b: BreakSignal) {
        }
    }

    private fun setIndex(obj: Any?, key: Any?, value: Any?) {
        if (obj !is LuaTable) throw LuaRuntimeException("attempt to index a ${typeName(obj)} value")
        if (key == null || !obj.entries.containsKey(key)) {
            val newIndex = obj.metatable?.rawGet("__newindex")
            if (newIndex is LuaTable) {
                setIndex(newIndex, key, value)
                return
            }
            if (newIndex is LuaCallable) {
                newIndex.call(listOf(obj, key, value))
                return
            }
        }
        obj.rawSet(key, value)
    }

    private fun getIndex(obj: Any?, key: Any?): Any? {
        if (obj is LuaTable) {
            val direct = obj.rawGet(key)
            if (direct != null) return direct
            val index = obj.metatable?.rawGet("__index") ?: return null
            return when (index) {
                is LuaTable -> getIndex(index, key)
                is LuaCallable -> index.call(listOf(obj, key)).firstOrNull()
                else -> null
            }
        }
        if (obj is String) {
            // Mirrors real Lua: strings have an implicit metatable whose __index is the string library.
            val stringLib = globals.get("string")
            if (stringLib is LuaTable) return stringLib.rawGet(key)
            return null
        }
        throw LuaRuntimeException("attempt to index a ${typeName(obj)} value")
    }

    private fun evalSingle(expr: Expr, env: Environment): Any? = when (expr) {
        is NumberLit -> expr.value
        is StringLit -> expr.value
        is BoolLit -> expr.value
        NilLit -> null
        Vararg -> env.lookupVarargs().firstOrNull()
        is VarRef -> env.get(expr.name)
        is Paren -> evalSingle(expr.expr, env)
        is IndexExpr -> getIndex(evalSingle(expr.obj, env), evalSingle(expr.key, env))
        is Unary -> evalUnary(expr, env)
        is Binary -> evalBinary(expr, env)
        is FunctionExpr -> LuaClosure(expr.params, expr.isVararg, expr.body, env, this)
        is TableCtor -> evalTableCtor(expr, env)
        is Call -> doCall(expr, env).firstOrNull()
        is MethodCall -> doMethodCall(expr, env).firstOrNull()
    }

    /** Only [Call], [MethodCall], and [Vararg] can yield more than one value. */
    private fun evalMulti(expr: Expr, env: Environment): List<Any?> = when (expr) {
        is Call -> doCall(expr, env)
        is MethodCall -> doMethodCall(expr, env)
        Vararg -> env.lookupVarargs()
        else -> listOf(evalSingle(expr, env))
    }

    /** All-but-last expr truncated to one value; the last expands fully (argument/return/local-list rule). */
    private fun evalExprList(exprs: List<Expr>, env: Environment): List<Any?> {
        if (exprs.isEmpty()) return emptyList()
        val result = mutableListOf<Any?>()
        for (i in 0 until exprs.size - 1) result.add(evalSingle(exprs[i], env))
        result.addAll(evalMulti(exprs.last(), env))
        return result
    }

    private fun doCall(expr: Call, env: Environment): List<Any?> {
        val callee = evalSingle(expr.callee, env)
        return callValue(callee, evalExprList(expr.args, env))
    }

    private fun doMethodCall(expr: MethodCall, env: Environment): List<Any?> {
        val obj = evalSingle(expr.obj, env)
        val method = getIndex(obj, expr.methodName)
        return callValue(method, listOf(obj) + evalExprList(expr.args, env))
    }

    private fun evalTableCtor(expr: TableCtor, env: Environment): LuaTable {
        val table = LuaTable()
        var arrayIndex = 1
        expr.fields.forEachIndexed { i, field ->
            when (field) {
                is NamedField -> table.rawSet(field.name, evalSingle(field.expr, env))
                is KeyedField -> table.rawSet(evalSingle(field.keyExpr, env), evalSingle(field.valueExpr, env))
                is PositionalField -> if (i == expr.fields.size - 1) {
                    for (v in evalMulti(field.expr, env)) {
                        table.rawSet(arrayIndex.toDouble(), v)
                        arrayIndex++
                    }
                } else {
                    table.rawSet(arrayIndex.toDouble(), evalSingle(field.expr, env))
                    arrayIndex++
                }
            }
        }
        return table
    }

    private fun evalUnary(expr: Unary, env: Environment): Any? = when (expr.op) {
        "-" -> evalSingle(expr.expr, env).let { v ->
            if (v is LuaTable) {
                val mm = v.metatable?.rawGet("__unm") as? LuaCallable ?: throw LuaRuntimeException("attempt to perform arithmetic on a table value")
                mm.call(listOf(v, v)).firstOrNull()
            } else {
                -asNumber(v)
            }
        }
        "not" -> !isTruthy(evalSingle(expr.expr, env))
        "#" -> luaLength(evalSingle(expr.expr, env))
        else -> throw LuaRuntimeException("Unknown unary operator '${expr.op}'")
    }

    private fun luaLength(value: Any?): Double = when (value) {
        is String -> value.length.toDouble()
        is LuaTable -> value.length().toDouble()
        else -> throw LuaRuntimeException("attempt to get length of a ${typeName(value)} value")
    }

    private fun evalBinary(expr: Binary, env: Environment): Any? {
        if (expr.op == "and") {
            val l = evalSingle(expr.left, env)
            return if (!isTruthy(l)) l else evalSingle(expr.right, env)
        }
        if (expr.op == "or") {
            val l = evalSingle(expr.left, env)
            return if (isTruthy(l)) l else evalSingle(expr.right, env)
        }
        val l = evalSingle(expr.left, env)
        val r = evalSingle(expr.right, env)
        return when (expr.op) {
            "+" -> arith("__add", l, r) { asNumber(l) + asNumber(r) }
            "-" -> arith("__sub", l, r) { asNumber(l) - asNumber(r) }
            "*" -> arith("__mul", l, r) { asNumber(l) * asNumber(r) }
            "/" -> arith("__div", l, r) { asNumber(l) / asNumber(r) }
            "%" -> arith("__mod", l, r) { asNumber(l).mod(asNumber(r)) }
            "^" -> arith("__pow", l, r) { Math.pow(asNumber(l), asNumber(r)) }
            ".." -> tostring(l) + tostring(r)
            "==" -> luaEquals(l, r)
            "~=" -> !luaEquals(l, r)
            "<" -> luaLess(l, r)
            ">" -> luaLess(r, l)
            "<=" -> luaLessOrEqual(l, r)
            ">=" -> luaLessOrEqual(r, l)
            else -> throw LuaRuntimeException("Unknown binary operator '${expr.op}'")
        }
    }

    private fun metamethod(value: Any?, event: String): LuaCallable? = (value as? LuaTable)?.metatable?.rawGet(event) as? LuaCallable

    /** `a op b`: a table operand dispatches to its (or the other operand's) metamethod, like real Lua. */
    private fun arith(event: String, l: Any?, r: Any?, plain: () -> Double): Any? {
        if (l is LuaTable || r is LuaTable) {
            val mm = metamethod(l, event) ?: metamethod(r, event)
                ?: throw LuaRuntimeException("attempt to perform arithmetic on a ${typeName(if (l is LuaTable) l else r)} value")
            return mm.call(listOf(l, r)).firstOrNull()
        }
        return plain()
    }

    /** Numbers compare as IEEE doubles (NaN is not equal to itself), unlike boxed `Double.equals`; two distinct tables use `__eq`. */
    private fun luaEquals(l: Any?, r: Any?): Boolean {
        if (l is Double && r is Double) {
            val a: Double = l
            val b: Double = r
            return a == b
        }
        if (l is LuaTable && r is LuaTable && l !== r) {
            val mm = metamethod(l, "__eq") ?: metamethod(r, "__eq")
            if (mm != null) return isTruthy(mm.call(listOf(l, r)).firstOrNull())
        }
        return l == r
    }

    private fun luaLess(l: Any?, r: Any?): Boolean {
        if (l is Double && r is Double) return l < r
        if (l is String && r is String) return l < r
        if (l is LuaTable || r is LuaTable) {
            val mm = metamethod(l, "__lt") ?: metamethod(r, "__lt")
            if (mm != null) return isTruthy(mm.call(listOf(l, r)).firstOrNull())
        }
        throw LuaRuntimeException("attempt to compare ${typeName(l)} with ${typeName(r)}")
    }

    private fun luaLessOrEqual(l: Any?, r: Any?): Boolean {
        if (l is LuaTable || r is LuaTable) {
            val mm = metamethod(l, "__le") ?: metamethod(r, "__le")
            if (mm != null) return isTruthy(mm.call(listOf(l, r)).firstOrNull())
            return !luaLess(r, l)
        }
        return luaLess(l, r) || luaEquals(l, r)
    }
}

/** Parses and runs a Lua source string in one call; returns the exit code (0 unless the script called `os.exit`). */
fun interpret(
    source: String,
    moduleLoader: ModuleLoader? = null,
    onOutput: (String) -> Unit = { kotlin.io.print(it) },
): Int {
    val chunk = LuaParser(LuaLexer(source).tokenize()).parseChunk()
    return try {
        Interpreter(onOutput, moduleLoader).run(chunk)
        0
    } catch (e: LuaExitException) {
        e.code
    }
}
