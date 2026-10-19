package kt2lua.interpreter

import java.util.concurrent.SynchronousQueue

/** A Lua coroutine. Each one runs its body on its own thread, but only one thread ever runs at a
 * time: [resume] hands control to the coroutine and blocks until it yields, returns or fails. */
class LuaThread internal constructor(private val interp: Interpreter, private val body: Any?) {
    var status = "suspended"
        private set

    private sealed class Outcome {
        class Yielded(val values: List<Any?>) : Outcome()
        class Finished(val values: List<Any?>) : Outcome()
        class Failed(val error: LuaRuntimeException) : Outcome()
    }

    private val toCoroutine = SynchronousQueue<List<Any?>>()
    private val toCaller = SynchronousQueue<Outcome>()
    private var started = false

    fun resume(args: List<Any?>): List<Any?> {
        if (status == "dead") throw LuaRuntimeException("cannot resume dead coroutine")
        if (status == "running") throw LuaRuntimeException("cannot resume non-suspended coroutine")
        val previous = current
        previous?.status = "normal"
        current = this
        status = "running"
        if (!started) {
            started = true
            val thread = Thread(null, {
                val outcome = try {
                    Outcome.Finished(interp.callValue(body, args))
                } catch (e: LuaRuntimeException) {
                    Outcome.Failed(e)
                } catch (e: Throwable) {
                    Outcome.Failed(LuaRuntimeException(e.message ?: e.toString()))
                }
                toCaller.put(outcome)
            }, "lua-coroutine", 32L * 1024 * 1024)
            thread.isDaemon = true
            thread.start()
        } else {
            toCoroutine.put(args)
        }
        val outcome = toCaller.take()
        current = previous
        previous?.status = "running"
        return when (outcome) {
            is Outcome.Yielded -> { status = "suspended"; outcome.values }
            is Outcome.Finished -> { status = "dead"; outcome.values }
            is Outcome.Failed -> { status = "dead"; throw outcome.error }
        }
    }

    internal fun yieldValues(values: List<Any?>): List<Any?> {
        toCaller.put(Outcome.Yielded(values))
        return toCoroutine.take()
    }

    override fun toString() = "thread: 0x${Integer.toHexString(System.identityHashCode(this))}"

    companion object {
        @Volatile var current: LuaThread? = null
    }
}

internal fun installCoroutines(interp: Interpreter, globals: Environment) {
    val lib = LuaTable()
    lib.rawSet("create", native("create") { a -> LuaThread(interp, a.getOrNull(0)) })
    lib.rawSet("resume", NativeFunction("resume") { a ->
        val co = a.getOrNull(0) as? LuaThread ?: throw LuaRuntimeException("bad argument #1 to 'resume' (coroutine expected)")
        try {
            listOf<Any?>(true) + co.resume(a.drop(1))
        } catch (e: LuaRuntimeException) {
            listOf(false, e.value)
        }
    })
    lib.rawSet("yield", NativeFunction("yield") { a ->
        val co = LuaThread.current ?: throw LuaRuntimeException("attempt to yield from outside a coroutine")
        co.yieldValues(a)
    })
    lib.rawSet("wrap", native("wrap") { a ->
        val co = LuaThread(interp, a.getOrNull(0))
        NativeFunction("wrap") { args -> co.resume(args) }
    })
    lib.rawSet("status", native("status") { a -> (a.getOrNull(0) as? LuaThread)?.status ?: "dead" })
    lib.rawSet("isyieldable", native("isyieldable") { LuaThread.current != null })
    lib.rawSet("running", NativeFunction("running") { listOf(LuaThread.current, LuaThread.current == null) })
    globals.define("coroutine", lib)
}
