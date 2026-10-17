package kt2lua.interpreter

/**
 * Runtime values are plain Kotlin values: `Double` for numbers, `String`, `Boolean`, `null` for
 * nil, [LuaTable] for tables, and [LuaCallable] for callables (closures or natives).
 */
class LuaTable {
    val entries = LinkedHashMap<Any, Any?>()
    var metatable: LuaTable? = null

    fun rawGet(key: Any?): Any? = key?.let { entries[it] }

    fun rawSet(key: Any?, value: Any?) {
        if (key == null) throw LuaRuntimeException("table index is nil")
        if (key is Double && key.isNaN()) throw LuaRuntimeException("table index is NaN")
        if (value == null) entries.remove(key) else entries[key] = value
    }

    /** The Lua `#` border: the length of the contiguous 1..n integer-keyed prefix. */
    fun length(): Int {
        var n = 0
        while (entries.containsKey((n + 1).toDouble())) n++
        return n
    }
}

interface LuaCallable {
    fun call(args: List<Any?>): List<Any?>
}

class NativeFunction(val name: String = "native", private val fn: (List<Any?>) -> List<Any?>) : LuaCallable {
    override fun call(args: List<Any?>): List<Any?> = fn(args)
}

/** Convenience for the common case of a native function that only ever returns one value. */
fun native(name: String, fn: (List<Any?>) -> Any?): NativeFunction = NativeFunction(name) { listOf(fn(it)) }

class LuaClosure(
    private val params: List<String>,
    private val isVararg: Boolean,
    private val body: List<Stmt>,
    private val closureEnv: Environment,
    private val interpreter: Interpreter,
) : LuaCallable {
    override fun call(args: List<Any?>): List<Any?> {
        val callEnv = Environment(closureEnv, isFunctionRoot = true)
        params.forEachIndexed { i, name -> callEnv.define(name, args.getOrNull(i)) }
        if (isVararg) {
            callEnv.varargs = if (args.size > params.size) args.subList(params.size, args.size) else emptyList()
        }
        return try {
            interpreter.execBlock(body, callEnv)
            emptyList()
        } catch (ret: ReturnSignal) {
            ret.values
        }
    }
}

/** Unwinds the Kotlin call stack back to the enclosing [LuaClosure.call] on `return`. */
internal class ReturnSignal(val values: List<Any?>) : RuntimeException(null, null, false, false)

/** Unwinds back to the nearest enclosing loop on `break`. */
internal class BreakSignal : RuntimeException(null, null, false, false)

/**
 * A lexical scope. Reading an undefined name returns nil (Lua semantics); assigning to a name
 * that isn't a local anywhere in the chain creates it as a global in the outermost scope,
 * mirroring Lua's "undeclared assignment creates a global" behavior.
 */
class Environment(private val parent: Environment? = null, val isFunctionRoot: Boolean = false) {
    private val values = mutableMapOf<String, Any?>()
    var varargs: List<Any?>? = null

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: String): Any? {
        var env: Environment? = this
        while (env != null) {
            if (env.values.containsKey(name)) return env.values[name]
            env = env.parent
        }
        return null
    }

    fun set(name: String, value: Any?) {
        var env: Environment? = this
        var root: Environment = this
        while (env != null) {
            if (env.values.containsKey(name)) {
                env.values[name] = value
                return
            }
            root = env
            env = env.parent
        }
        root.values[name] = value
    }

    fun lookupVarargs(): List<Any?> {
        var env: Environment? = this
        while (env != null) {
            env.varargs?.let { return it }
            if (env.isFunctionRoot) break
            env = env.parent
        }
        throw LuaRuntimeException("cannot use '...' outside a vararg function")
    }
}

fun isTruthy(value: Any?): Boolean = value != null && value != false

fun typeName(value: Any?): String = when (value) {
    null -> "nil"
    is Boolean -> "boolean"
    is Double -> "number"
    is String -> "string"
    is LuaTable -> "table"
    is LuaCallable -> "function"
    else -> "userdata"
}

fun asNumber(value: Any?): Double = when (value) {
    is Double -> value
    is String -> value.trim().toDoubleOrNull()
        ?: throw LuaRuntimeException("attempt to perform arithmetic on a string value")
    else -> throw LuaRuntimeException("attempt to perform arithmetic on a ${typeName(value)} value")
}

/**
 * [invoke] lets callers (the interpreter) route `__tostring` metamethod calls back through
 * function-call machinery without this file depending on [Interpreter].
 */
fun luaToString(value: Any?, invoke: ((LuaCallable, List<Any?>) -> List<Any?>)? = null): String = when (value) {
    null -> "nil"
    is Boolean -> value.toString()
    is Double -> formatLuaNumber(value)
    is String -> value
    is LuaTable -> {
        val metamethod = value.metatable?.rawGet("__tostring")
        if (metamethod is LuaCallable && invoke != null) {
            luaToString(invoke(metamethod, listOf(value)).firstOrNull())
        } else {
            "table: 0x${Integer.toHexString(System.identityHashCode(value))}"
        }
    }
    is LuaCallable -> "function: 0x${Integer.toHexString(System.identityHashCode(value))}"
    else -> value.toString()
}

private fun formatLuaNumber(value: Double): String {
    if (value.isNaN()) return "nan"
    if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
