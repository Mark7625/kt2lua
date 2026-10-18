package kt2lua.transpiler

enum class TokenType {
    FUN, VAL, VAR, IF, ELSE, FOR, IN, WHILE, RETURN,
    TRUE, FALSE, NULL, PACKAGE, IMPORT, UNTIL, STEP, PRIVATE, AS, OBJECT, CONST, WHEN, IS,
    CLASS, THIS, TRY, THROW,

    /** A real Kotlin keyword kt2lua does not implement yet. Carries the keyword text so the
     * parser can report "X is not supported" instead of silently mis-parsing it. */
    UNSUPPORTED_KEYWORD,

    IDENTIFIER, INT_LITERAL, DOUBLE_LITERAL, STRING_TEMPLATE,

    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, COMMA, COLON, DOT, DOT_DOT, DOUBLE_COLON,
    QUESTION, ARROW, SAFE_DOT, ELVIS, NOT_NULL_ASSERT,

    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    EQ, NEQ, LT, GT, LTE, GTE,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    AND_AND, OR_OR, NOT,

    EOF,
}

sealed class TemplatePart {
    data class Text(val text: String) : TemplatePart()
    data class ExprSource(val source: String) : TemplatePart()
}

data class Token(
    val type: TokenType,
    val text: String? = null,
    val intValue: Int? = null,
    val doubleValue: Double? = null,
    val templateParts: List<TemplatePart>? = null,
    val line: Int,
    val col: Int,
)
