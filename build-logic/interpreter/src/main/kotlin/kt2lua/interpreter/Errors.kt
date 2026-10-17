package kt2lua.interpreter

class LuaLexException(message: String, val line: Int) : RuntimeException("$message (line $line)")
class LuaParseException(message: String, val line: Int) : RuntimeException("$message (line $line)")

/** [value] is the real Lua error value — real Lua's `error(v)` accepts any value, not just
 * strings, and `pcall`/`xpcall` must hand that exact value back. Defaults to [message] so a
 * plain-string `throw LuaRuntimeException("...")` needs no change. */
class LuaRuntimeException(message: String, val value: Any? = message) : RuntimeException(message)
