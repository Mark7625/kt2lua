package kt2lua.interpreter

enum class TokenType {
    LOCAL, FUNCTION, END, IF, THEN, ELSE, ELSEIF, FOR, WHILE, DO, RETURN,
    TRUE, FALSE, NIL, AND, OR, NOT, BREAK, REPEAT, UNTIL, IN,

    IDENTIFIER, NUMBER, STRING,

    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, DOT_DOT, ELLIPSIS, COLON, SEMICOLON, HASH,

    ASSIGN, EQ, NEQ, LT, GT, LTE, GTE,
    PLUS, MINUS, STAR, SLASH, PERCENT, CARET,

    EOF,
}

data class Token(
    val type: TokenType,
    val text: String? = null,
    val numberValue: Double? = null,
    val line: Int,
)
