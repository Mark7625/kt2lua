package kt2lua.interpreter

class LuaLexException(message: String, val line: Int) : RuntimeException("$message (line $line)")
class LuaParseException(message: String, val line: Int) : RuntimeException("$message (line $line)")

/** [value] is the real Lua error value — real Lua's `error(v)` accepts any value, not just
 * strings, and `pcall`/`xpcall` must hand that exact value back. Defaults to [message] so a
 * plain-string `throw LuaRuntimeException("...")` needs no change. */
/** Thrown by `os.exit`; [interpret] turns it into the script's exit code. */
class LuaExitException(val code: Int) : RuntimeException("exit $code")

class LuaRuntimeException(message: String, val value: Any? = message) : RuntimeException(message)
