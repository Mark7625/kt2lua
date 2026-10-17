package kt2lua.common

/** Generic over the token/token-type pair via [typeOf] so each subsystem keeps its own token
 * definitions instead of sharing an interface. */
class TokenCursor<T, Type>(
    private val tokens: List<T>,
    private val typeOf: (T) -> Type,
    private val eof: Type,
) {
    var pos: Int = 0
        private set

    fun current(): T = tokens[pos]
    fun peekType(offset: Int): Type? = tokens.getOrNull(pos + offset)?.let(typeOf)
    fun isAtEnd(): Boolean = typeOf(current()) == eof
    fun check(type: Type): Boolean = typeOf(current()) == type

    fun advance(): T {
        val t = current()
        if (!isAtEnd()) pos++
        return t
    }

    fun match(type: Type): Boolean {
        if (check(type)) {
            advance()
            return true
        }
        return false
    }

    /** [onMismatch] is a callback instead of a fixed exception type so each subsystem keeps its
     * own parse-error type/message/position fields. */
    fun expect(type: Type, onMismatch: (T) -> Nothing): T {
        if (!check(type)) onMismatch(current())
        return advance()
    }
}
