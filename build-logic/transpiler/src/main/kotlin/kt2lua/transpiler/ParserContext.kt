package kt2lua.transpiler

/** Shared parsing primitives a statement feature needs, so it depends on only the grammar it
 * actually uses rather than [Parser]'s entire internal surface. */
interface ParserContext {
    fun current(): Token
    fun peekType(offset: Int): TokenType?
    fun isAtEnd(): Boolean
    fun check(type: TokenType): Boolean
    fun advance(): Token
    fun match(type: TokenType): Boolean
    fun expect(type: TokenType): Token
    fun expectIdentifierName(): String

    fun parseStatement(): Statement
    fun parseBlock(): Block
    fun parseBranch(): Block
    fun parseExpression(): Expression
    fun parseExpressionBranch(): Expression
    /** Additive precedence or tighter — range bounds (`for`, `when`'s `in a..b`) need this
     * instead of a full [parseExpression] so `..`/`until`/`step` aren't swallowed. */
    fun parseAdditive(): Expression
    fun parseTypeName(): TypeName
    fun parseCallArgs(): List<CallArg>
    fun parseFunctionDecl(isPrivate: Boolean): FunctionDecl
    fun parseVariableDecl(isPrivate: Boolean): VariableDecl
    fun unsupportedKeywordError(): ParseException
}

/** [supports] decides purely by looking at upcoming tokens — never consuming any — whether this
 * feature owns the statement starting here; [Parser.parseStatement] tries each registered
 * feature in order and uses the first match. */
interface StatementParser {
    fun supports(ctx: ParserContext): Boolean
    fun parse(ctx: ParserContext): Statement
}
