package kt2lua.transpiler

import kt2lua.common.TokenCursor
import kt2lua.transpiler.parser.ClassFeature
import kt2lua.transpiler.parser.ForFeature
import kt2lua.transpiler.parser.IfFeature
import kt2lua.transpiler.parser.ObjectFeature
import kt2lua.transpiler.parser.ThrowFeature
import kt2lua.transpiler.parser.TryFeature
import kt2lua.transpiler.parser.WhenFeature
import kt2lua.transpiler.parser.WhileFeature

private val EQUALITY_OPS = mapOf(TokenType.EQ to "==", TokenType.NEQ to "!=")
private val RELATIONAL_OPS = mapOf(
    TokenType.LT to "<", TokenType.GT to ">", TokenType.LTE to "<=", TokenType.GTE to ">=",
)
private val ADDITIVE_OPS = mapOf(TokenType.PLUS to "+", TokenType.MINUS to "-")
private val MULTIPLICATIVE_OPS = mapOf(
    TokenType.STAR to "*", TokenType.SLASH to "/", TokenType.PERCENT to "%",
)

/** Index into [Parser.binaryLevels] for the additive level — see [Parser.parseAdditive]. */
private const val ADDITIVE_LEVEL = 9

/** One precedence level of this subset's binary operators, loosest-binding first. [matchOp]
 * recognizes and consumes this level's operator at the current token, or returns null without
 * advancing. [build] defaults to a plain [BinaryExpr] since only `?:` and `to` produce
 * something else. */
private class BinaryLevel(
    val matchOp: () -> String?,
    val build: (op: String, left: Expression, right: Expression) -> Expression = { op, l, r -> BinaryExpr(op, l, r) },
)
private val ASSIGN_OPS = mapOf(
    TokenType.ASSIGN to "=", TokenType.PLUS_ASSIGN to "+=", TokenType.MINUS_ASSIGN to "-=",
    TokenType.STAR_ASSIGN to "*=", TokenType.SLASH_ASSIGN to "/=", TokenType.PERCENT_ASSIGN to "%=",
)

/** Statements are not newline- or semicolon-sensitive: a statement simply parses until the
 * grammar can't extend it further, since none of our statement-starting keywords can also
 * continue an expression.
 *
 * Every statement-level construct ([ObjectFeature], [ClassFeature], [IfFeature], [WhenFeature],
 * [ForFeature], [WhileFeature], [ThrowFeature], [TryFeature]) owns its own parsing logic as a
 * [StatementParser]; this class keeps only what's shared — token navigation, expression
 * parsing, and grammar more than one feature needs — exposed via [ParserContext].
 */
class Parser(private val tokens: List<Token>, literalConstants: Map<String, String> = emptyMap(), private val bindings: LuaBindings = LuaBindings()) : ParserContext {
    private val literalConstants = literalConstants.toMutableMap()
    private val cursor = TokenCursor(tokens, Token::type, TokenType.EOF)
    private val pos get() = cursor.pos

    private val ifFeature = IfFeature()
    private val whenFeature = WhenFeature()
    private val statementFeatures: List<StatementParser> = listOf(
        ObjectFeature(),
        ClassFeature(),
        ifFeature,
        whenFeature,
        ForFeature(),
        WhileFeature(),
        ThrowFeature(),
        TryFeature(),
    )

    fun parseProgram(): Program {
        val body = mutableListOf<Statement>()
        while (!isAtEnd()) {
            if (match(TokenType.SEMICOLON)) continue
            if (check(TokenType.PACKAGE)) {
                skipPackageDirective()
                continue
            }
            if (check(TokenType.IMPORT)) {
                parseImportOrNull()?.let { body.add(it) }
                continue
            }
            body.add(parseStatement())
        }
        return Program(body)
    }

    private fun skipPackageDirective() {
        advance()
        while (check(TokenType.IDENTIFIER) || check(TokenType.DOT) || check(TokenType.STAR)) advance()
    }

    /** `import a.b.*` (wildcard) and imports from the Kotlin/Java standard libraries are no-ops and return null. */
    private fun parseImportOrNull(): ImportStatement? {
        expect(TokenType.IMPORT)
        val segments = mutableListOf(expectIdentifierName())
        while (match(TokenType.DOT)) {
            if (match(TokenType.STAR)) return null
            segments.add(expectIdentifierName())
        }
        val alias = if (match(TokenType.AS)) expectIdentifierName() else segments.last()
        if (segments.first() == "kotlin" || segments.first() == "java") return null
        if (segments.joinToString(".") == "com.mark.runescape.extended.Subscribe") return null
        if (bindings.type(segments.joinToString(".")) != null) return null
        val constantPrefix = segments.joinToString(".") + "."
        val importedConstants = literalConstants.filterKeys { it.startsWith(constantPrefix) }
        if (importedConstants.isNotEmpty()) {
            for ((name, value) in importedConstants) literalConstants[alias + "." + name.removePrefix(constantPrefix)] = value
            return null
        }
        return ImportStatement(segments.joinToString("/"), alias)
    }

    override fun parseStatement(): Statement {
        if (check(TokenType.UNSUPPORTED_KEYWORD)) throw unsupportedKeywordError()
        if (check(TokenType.PRIVATE)) {
            advance()
            return parseStatementModifiers()
        }
        if (check(TokenType.CONST)) {
            advance()
            return parseVariableDecl(isPrivate = false)
        }
        if (check(TokenType.FUN)) return parseFunctionDecl(isPrivate = false)
        for (feature in statementFeatures) if (feature.supports(this)) return feature.parse(this)
        if ((check(TokenType.VAL) || check(TokenType.VAR)) && peekType(1) == TokenType.LPAREN) {
            return parseDestructuringDecl(isPrivate = false)
        }
        if (check(TokenType.VAL) || check(TokenType.VAR)) return parseVariableDecl(isPrivate = false)
        if (check(TokenType.RETURN)) return parseReturnStatement()
        if (check(TokenType.LBRACE)) return parseBlock()
        return parseExpressionOrAssignment()
    }

    /** An assignment target isn't knowable from the first token alone (`x = 1` and `obj.field =
     * 1` both start with an identifier), so this parses one expression unconditionally, then
     * checks for an assignment operator and converts what was parsed into an [AssignTarget]. */
    private fun parseExpressionOrAssignment(): Statement {
        val expr = parseExpression()
        val opToken = current()
        val op = ASSIGN_OPS[opToken.type] ?: return ExpressionStatement(expr)
        advance()
        val target = exprToAssignTarget(expr)
            ?: throw ParseException("Invalid assignment target", opToken.line, opToken.col)
        return Assignment(target, op, parseExpression())
    }

    private fun exprToAssignTarget(expr: Expression): AssignTarget? = when (expr) {
        is Identifier -> NameTarget(expr.name)
        is PropertyAccessExpr -> PropertyTarget(expr.obj, expr.name)
        is IndexExpr -> IndexTarget(expr.obj, expr.index)
        else -> null
    }

    private fun parseStatementModifiers(): Statement {
        if (match(TokenType.CONST)) return parseVariableDecl(isPrivate = true)
        if (check(TokenType.FUN)) return parseFunctionDecl(isPrivate = true)
        if ((check(TokenType.VAL) || check(TokenType.VAR)) && peekType(1) == TokenType.LPAREN) {
            return parseDestructuringDecl(isPrivate = true)
        }
        return parseVariableDecl(isPrivate = true)
    }

    /** `val (a, b) = pair` — already known (by [peekType] lookahead) to have `(` right after
     * `val`/`var`, so this just consumes the name list unconditionally. */
    private fun parseDestructuringDecl(isPrivate: Boolean): Statement {
        val mutable = check(TokenType.VAR)
        advance()
        expect(TokenType.LPAREN)
        val names = mutableListOf(expectIdentifierName())
        while (match(TokenType.COMMA)) names.add(expectIdentifierName())
        expect(TokenType.RPAREN)
        expect(TokenType.ASSIGN)
        return DestructuringDecl(names, mutable, parseExpression(), isPrivate)
    }

    override fun unsupportedKeywordError(): ParseException {
        val tok = current()
        return ParseException(
            "Kotlin '${tok.text}' is not supported by kt2lua's subset yet. " +
                "See README's feature matrix for current coverage.",
            tok.line,
            tok.col,
        )
    }

    override fun parseFunctionDecl(isPrivate: Boolean): FunctionDecl {
        expect(TokenType.FUN)
        val name = expectIdentifierName()
        expect(TokenType.LPAREN)
        val params = mutableListOf<Param>()
        if (!check(TokenType.RPAREN)) {
            do {
                // `vararg` is a soft keyword — recognized contextually here, not lexed as a
                // hard keyword, since it's a valid plain identifier elsewhere.
                val isVararg = check(TokenType.IDENTIFIER) && current().text == "vararg"
                if (isVararg) advance()
                val pname = expectIdentifierName()
                expect(TokenType.COLON)
                val rawType = current().text ?: ""
                val ptype = parseTypeName()
                val default = if (match(TokenType.ASSIGN)) parseExpression() else null
                params.add(Param(pname, ptype, rawType, isVararg, default))
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN)
        params.dropLast(1).firstOrNull { it.isVararg }?.let {
            throw ParseException("'vararg' is only allowed on a function's last parameter", current().line, current().col)
        }
        var returnType: TypeName? = null
        var rawReturnType = ""
        if (match(TokenType.COLON)) {
            rawReturnType = current().text ?: ""
            returnType = parseTypeName()
        }
        if (match(TokenType.ASSIGN)) {
            return FunctionDecl(name, params, returnType, ExprBody(parseExpression()), isPrivate, rawReturnType)
        }
        return FunctionDecl(name, params, returnType, BlockBody(parseBlock()), isPrivate, rawReturnType)
    }

    override fun parseTypeName(): TypeName {
        if (check(TokenType.LPAREN)) {
            // `(Int, Int) -> Int` — a function type: all of it is consumed and the whole thing is
            // tracked as an untyped value, since a Lua function needs no signature.
            var depth = 0
            do {
                if (check(TokenType.LPAREN)) depth++
                if (check(TokenType.RPAREN)) depth--
                advance()
            } while (depth > 0)
            expect(TokenType.ARROW)
            parseTypeName()
            match(TokenType.QUESTION)
            return TypeName.ANY
        }
        val type = normalizeType(expectIdentifierName())
        // `MutableSet<Int>`, `Map<String, Int>`, ... — a type position is never ambiguous with
        // the `<` comparison operator, so these are always skipped unconditionally.
        if (check(TokenType.LT)) skipGenericArgs()
        match(TokenType.QUESTION) // trailing `?` on a nullable type: consumed, no effect
        return type
    }

    override fun parseVariableDecl(isPrivate: Boolean): VariableDecl {
        val mutable = check(TokenType.VAR)
        advance()
        val name = expectIdentifierName()
        var varType: TypeName? = null
        var rawType = ""
        if (match(TokenType.COLON)) {
            rawType = current().text ?: ""
            varType = parseTypeName()
        }
        expect(TokenType.ASSIGN)
        return VariableDecl(name, varType, mutable, parseExpression(), isPrivate, rawType)
    }

    override fun parseBlock(): Block {
        expect(TokenType.LBRACE)
        val body = mutableListOf<Statement>()
        while (!check(TokenType.RBRACE)) {
            if (match(TokenType.SEMICOLON)) continue
            body.add(parseStatement())
        }
        expect(TokenType.RBRACE)
        return Block(body)
    }

    override fun parseBranch(): Block =
        if (check(TokenType.LBRACE)) parseBlock() else Block(listOf(parseStatement()))

    private fun parseReturnStatement(): ReturnStatement {
        val keyword = expect(TokenType.RETURN)
        return if (current().line == keyword.line && canStartExpression()) ReturnStatement(parseExpression()) else ReturnStatement(null)
    }

    private fun canStartExpression(): Boolean = when (current().type) {
        TokenType.INT_LITERAL, TokenType.DOUBLE_LITERAL, TokenType.STRING_TEMPLATE, TokenType.CHAR_LITERAL,
        TokenType.TRUE, TokenType.FALSE, TokenType.NULL, TokenType.IDENTIFIER, TokenType.LPAREN,
        TokenType.MINUS, TokenType.NOT, TokenType.PLUS, TokenType.IF, TokenType.WHEN, TokenType.THIS,
        TokenType.DOUBLE_COLON, TokenType.LBRACE -> true
        else -> false
    }

    private fun matchTokenOp(ops: Map<TokenType, String>): String? {
        val op = ops[current().type] ?: return null
        advance()
        return op
    }

    /** `a to b` — the one infix-call-style stdlib function this subset recognizes (for
     * `mapOf(k to v, ...)`), hard-coded rather than general `infix fun` support. */
    private fun matchInfixTo(): String? {
        if (check(TokenType.IDENTIFIER) && current().text == "to") {
            advance()
            return "to"
        }
        return null
    }

    /** `a..b`, `a until b`, `a downTo b`. */
    private fun matchRangeOp(): String? {
        if (match(TokenType.DOT_DOT)) return ".."
        if (match(TokenType.UNTIL)) return "until"
        if (check(TokenType.IDENTIFIER) && current().text == "downTo") {
            advance()
            return "downTo"
        }
        return null
    }

    /** `x !in xs` — `!` and `in` as two separate tokens (unlike `!=`), only recognized here in
     * infix position so it can't be confused with a unary `!` at the start of an operand. A
     * membership operator starting a new line is never a continuation: in a `when`, it begins the
     * next branch's `in a..b` condition. */
    private fun matchInOp(): String? {
        val previous = tokens.getOrNull(cursor.pos - 1)
        if (previous != null && previous.line < current().line && (check(TokenType.IN) || check(TokenType.NOT))) return null
        if (check(TokenType.NOT) && peekType(1) == TokenType.IN) {
            advance()
            advance()
            return "!in"
        }
        if (match(TokenType.IN)) return "in"
        return null
    }

    /** Loosest-to-tightest, matching real Kotlin's relative precedence: `||`, `&&`, `==`/`!=`,
     * `<`/`>`/`<=`/`>=`, `in`/`!in`, `?:`, `to`, `step`, `..`/`until`/`downTo`, `+`/`-`, `*`/`/`/`%`. */
    private val binaryLevels: List<BinaryLevel> = listOf(
        BinaryLevel({ if (match(TokenType.OR_OR)) "||" else null }),
        BinaryLevel({ if (match(TokenType.AND_AND)) "&&" else null }),
        BinaryLevel({ matchTokenOp(EQUALITY_OPS) }),
        BinaryLevel({ matchTokenOp(RELATIONAL_OPS) }),
        BinaryLevel({ matchInOp() }),
        BinaryLevel({ if (match(TokenType.ELVIS)) "?:" else null }, build = { _, l, r -> ElvisExpr(l, r) }),
        BinaryLevel({ matchInfixTo() }, build = { _, l, r -> ToPairExpr(l, r) }),
        BinaryLevel({ if (match(TokenType.STEP)) "step" else null }, build = { _, l, r ->
            (l as? RangeExpr)?.copy(step = r) ?: error("'step' is only supported on a range, like 1..10 step 2")
        }),
        BinaryLevel({ matchRangeOp() }, build = { op, l, r -> RangeExpr(l, r, inclusive = op != "until", descending = op == "downTo") }),
        BinaryLevel({ matchTokenOp(ADDITIVE_OPS) }),
        BinaryLevel({ matchTokenOp(MULTIPLICATIVE_OPS) }),
    )

    override fun parseExpression(): Expression = parseBinary(0)

    /** Used by [ForFeature]/[WhenFeature]'s range bounds, which must stop before
     * `..`/`until`/`step` so those aren't swallowed by a looser operator. */
    override fun parseAdditive(): Expression = parseBinary(ADDITIVE_LEVEL)

    /** Climbs [binaryLevels] from loosest to tightest, recursing one level tighter for each
     * operand. All levels are left-associative. */
    private fun parseBinary(level: Int): Expression {
        if (level >= binaryLevels.size) return parseUnary()
        var left = parseBinary(level + 1)
        val binLevel = binaryLevels[level]
        while (true) {
            val op = binLevel.matchOp() ?: break
            left = binLevel.build(op, left, parseBinary(level + 1))
        }
        return left
    }

    private fun parseUnary(): Expression = when {
        match(TokenType.MINUS) -> UnaryExpr("-", parseUnary())
        match(TokenType.NOT) -> UnaryExpr("!", parseUnary())
        match(TokenType.PLUS) -> parseUnary()
        else -> parsePrimary()
    }

    override fun parseExpressionBranch(): Expression {
        if (match(TokenType.LBRACE)) {
            val expr = parseExpression()
            expect(TokenType.RBRACE)
            return expr
        }
        return parseExpression()
    }

    private fun parsePrimary(): Expression {
        var expr = parseDotChain(parseAtom())
        while (true) {
            if (check(TokenType.LBRACKET)) {
                expr = parseDotChain(parseIndexSuffix(expr))
            } else if (check(TokenType.LPAREN) && cursor.pos > 0 && tokens[cursor.pos - 1].line == current().line &&
                (expr is CallExpr || expr is MethodCallExpr || expr is IndexExpr || expr is ParenExpr)
            ) {
                // `f(x)(y)` / `fs[0](y)` - invoking the value a call/index produced.
                expr = parseDotChain(MethodCallExpr(expr, "invoke", parseCallArgs()))
            } else {
                break
            }
        }
        while (match(TokenType.NOT_NULL_ASSERT)) {
            expr = parseDotChain(NotNullAssertExpr(expr))
        }
        // `expr as Type`: parsed and discarded — Lua is dynamically typed and needs no cast.
        while (match(TokenType.AS)) {
            match(TokenType.QUESTION) // tolerate `as?`
            parseTypeName()
        }
        return expr
    }

    /** A chain with no `?.` folds to plain nested [PropertyAccessExpr]/[MethodCallExpr]; one
     * with at least one `?.` becomes a single flat [SafeChainExpr] instead. */
    private fun parseDotChain(base: Expression): Expression {
        val steps = mutableListOf<ChainStep>()
        var sawSafe = false
        while (check(TokenType.DOT) || check(TokenType.SAFE_DOT)) {
            val safe = check(TokenType.SAFE_DOT)
            advance()
            sawSafe = sawSafe || safe
            val name = expectIdentifierName()
            steps.add(
                if (check(TokenType.LPAREN) || check(TokenType.LBRACE)) ChainCall(name, parseArgsWithTrailingLambda(), safe)
                else ChainProp(name, safe)
            )
        }
        if (!sawSafe && base is Identifier && steps.all { it is ChainProp }) {
            val name = (listOf(base.name) + steps.map { (it as ChainProp).name }).joinToString(".")
            literalConstants[name]?.let { value ->
                if (value.startsWith("@lookup:")) {
                    val lookup = value.removePrefix("@lookup:")
                    val target = lookup.substringBefore(':')
                    require(lookup.contains(':') && target.split('.').all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }) { "Invalid lookup constant binding" }
                    val parts = target.split('.')
                    val receiver = parts.drop(1).fold(Identifier(parts.first()) as Expression) { expr, part -> PropertyAccessExpr(expr, part) }
                    return IndexExpr(receiver, StringTemplateLiteral(listOf(TemplateExprPart.Text(lookup.substringAfter(':')))))
                }
                return StringTemplateLiteral(listOf(TemplateExprPart.Text(value)))
            }
        }
        return if (steps.isEmpty()) base else if (sawSafe) SafeChainExpr(base, steps) else foldChain(base, steps)
    }

    private fun foldChain(base: Expression, steps: List<ChainStep>): Expression {
        var expr = base
        for (step in steps) {
            expr = when (step) {
                is ChainProp -> PropertyAccessExpr(expr, step.name)
                is ChainCall -> MethodCallExpr(expr, step.name, step.args)
            }
        }
        return expr
    }

    private fun parseIndexSuffix(base: Expression): Expression {
        expect(TokenType.LBRACKET)
        val index = parseExpression()
        expect(TokenType.RBRACKET)
        return IndexExpr(base, index)
    }

    private fun parseAtom(): Expression {
        val tok = current()
        return when (tok.type) {
            TokenType.INT_LITERAL -> {
                advance(); IntLiteral(tok.intValue!!)
            }
            TokenType.DOUBLE_LITERAL -> {
                advance(); DoubleLiteral(tok.doubleValue!!)
            }
            TokenType.TRUE -> {
                advance(); BooleanLiteral(true)
            }
            TokenType.FALSE -> {
                advance(); BooleanLiteral(false)
            }
            TokenType.NULL -> {
                advance(); NullLiteral
            }
            TokenType.STRING_TEMPLATE -> {
                advance(); buildTemplateLiteral(tok.templateParts!!)
            }
            TokenType.CHAR_LITERAL -> {
                advance(); CharLiteral(tok.text!![0])
            }
            TokenType.LPAREN -> {
                advance()
                val expr = parseExpression()
                expect(TokenType.RPAREN)
                ParenExpr(expr)
            }
            TokenType.IF -> ifFeature.parseIfExpr(this)
            TokenType.RETURN -> ReturnExpr(parseReturnStatement().expr)
            TokenType.WHEN -> whenFeature.parseWhenExpr(this)
            // Lua's colon-method convention names the implicit receiver `self`, not `this` —
            // emitting it as plain `self` lets `this.x` flow through the ordinary
            // property-access path with zero special-casing.
            TokenType.THIS -> {
                advance(); Identifier("self")
            }
            TokenType.DOUBLE_COLON -> {
                advance(); FunctionRef(expectIdentifierName())
            }
            TokenType.IDENTIFIER -> {
                advance()
                // `emptyList<Module>()`-style call-site type arguments: parsed and discarded,
                // this subset erases all generics.
                if (check(TokenType.LT) && looksLikeGenericCallArgs()) skipGenericArgs()
                if (check(TokenType.LPAREN) || check(TokenType.LBRACE)) CallExpr(tok.text!!, parseArgsWithTrailingLambda())
                else Identifier(tok.text!!)
            }
            TokenType.LBRACE -> parseLambda()
            TokenType.UNSUPPORTED_KEYWORD -> throw unsupportedKeywordError()
            else -> throw ParseException("Unexpected token '${tok.type}'", tok.line, tok.col)
        }
    }

    /** `<` after an identifier is ambiguous between `a < b` and a call-site type-argument list.
     * Scans forward tracking angle-bracket depth, accepting only identifiers/commas/dots; it's
     * a generic argument list only if the scan reaches a balanced `>` immediately followed by
     * `(` or `{` (a call with no parens but a trailing lambda, like `compareBy<Person> { ... }`)
     * — anything else is a real `<` comparison. */
    private fun looksLikeGenericCallArgs(): Boolean {
        var i = pos
        var depth = 0
        while (i < tokens.size) {
            when (tokens[i].type) {
                TokenType.LT -> depth++
                TokenType.GT -> {
                    depth--
                    if (depth == 0) return i + 1 < tokens.size && (tokens[i + 1].type == TokenType.LPAREN || tokens[i + 1].type == TokenType.LBRACE)
                }
                TokenType.IDENTIFIER, TokenType.COMMA, TokenType.DOT -> {}
                else -> return false
            }
            i++
        }
        return false
    }

    private fun skipGenericArgs() {
        expect(TokenType.LT)
        var depth = 1
        while (depth > 0) {
            when (current().type) {
                TokenType.LT -> {
                    depth++
                    advance()
                }
                TokenType.GT -> {
                    depth--
                    advance()
                }
                else -> advance()
            }
        }
    }

    override fun parseCallArgs(): List<CallArg> {
        expect(TokenType.LPAREN)
        val args = mutableListOf<CallArg>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseCallArg())
            } while (match(TokenType.COMMA))
        }
        val closeParen = expect(TokenType.RPAREN)
        if (args.dropLast(1).any { it is SpreadArg }) {
            throw ParseException("Spread operator '*' is only supported as a call's last argument in this subset", closeParen.line, closeParen.col)
        }
        return args
    }

    /** `foo(a, b) { lambda }` / `foo { lambda }` — real Kotlin's trailing-lambda call sugar. */
    private fun parseArgsWithTrailingLambda(): List<CallArg> {
        val args = if (check(TokenType.LPAREN)) parseCallArgs().toMutableList() else mutableListOf()
        if (check(TokenType.LBRACE)) args.add(PositionalArg(parseLambda()))
        return args
    }

    /** `{ x, y -> body }` / `{ (k, v) -> body }` (a Map entry destructured into two names) /
     * `{ body }` (implicit `it`). Distinguishing an explicit param list from a body that happens
     * to start the same way (`{ x + 1 }`) needs a bounded lookahead for `(IDENTIFIER ,)*
     * IDENTIFIER ->` before committing to either reading. */
    private fun parseLambda(): LambdaExpr {
        expect(TokenType.LBRACE)
        val params = mutableListOf<String>()
        if (hasDestructuredLambdaParamList()) {
            expect(TokenType.LPAREN)
            do {
                params.add(expectIdentifierName())
            } while (match(TokenType.COMMA))
            expect(TokenType.RPAREN)
            expect(TokenType.ARROW)
        } else if (hasLambdaParamList()) {
            do {
                params.add(expectIdentifierName())
                if (match(TokenType.COLON)) parseTypeName()
            } while (match(TokenType.COMMA))
            expect(TokenType.ARROW)
        }
        val body = mutableListOf<Statement>()
        while (!check(TokenType.RBRACE)) {
            if (match(TokenType.SEMICOLON)) continue
            body.add(parseStatement())
        }
        expect(TokenType.RBRACE)
        return LambdaExpr(params, Block(body))
    }

    /** `(k, v) ->` right after `{` — a destructured single lambda parameter (here, always a Map
     * entry), treated the same as two real parameters at the AST level. */
    private fun hasDestructuredLambdaParamList(): Boolean {
        if (peekType(0) != TokenType.LPAREN) return false
        var offset = 1
        if (peekType(offset) != TokenType.IDENTIFIER) return false
        offset++
        while (peekType(offset) == TokenType.COMMA) {
            offset++
            if (peekType(offset) != TokenType.IDENTIFIER) return false
            offset++
        }
        if (peekType(offset) != TokenType.RPAREN) return false
        offset++
        return peekType(offset) == TokenType.ARROW
    }

    private fun hasLambdaParamList(): Boolean {
        var offset = scanOneLambdaParam(0) ?: return false
        while (peekType(offset) == TokenType.COMMA) {
            offset = scanOneLambdaParam(offset + 1) ?: return false
        }
        return peekType(offset) == TokenType.ARROW
    }

    /** Returns the offset just past one `name` or `name: Type` lambda parameter starting at
     * [start], or null if [start] doesn't start a parameter at all. */
    private fun scanOneLambdaParam(start: Int): Int? {
        var offset = start
        if (peekType(offset) != TokenType.IDENTIFIER) return null
        offset++
        if (peekType(offset) == TokenType.COLON) {
            offset++
            if (peekType(offset) != TokenType.IDENTIFIER) return null
            offset++
            if (peekType(offset) == TokenType.QUESTION) offset++
        }
        return offset
    }

    private fun parseCallArg(): CallArg {
        if (match(TokenType.STAR)) return SpreadArg(parseExpression())
        if (check(TokenType.IDENTIFIER) && peekType(1) == TokenType.ASSIGN) {
            val name = expectIdentifierName()
            expect(TokenType.ASSIGN)
            return NamedArg(name, parseExpression())
        }
        return PositionalArg(parseExpression())
    }

    private fun buildTemplateLiteral(parts: List<TemplatePart>): StringTemplateLiteral {
        val built = parts.map { part ->
            when (part) {
                is TemplatePart.Text -> TemplateExprPart.Text(part.text)
                is TemplatePart.ExprSource -> {
                    val subTokens = Lexer(part.source).tokenize()
                    TemplateExprPart.Expr(Parser(subTokens, literalConstants).parseExpression())
                }
            }
        }
        return StringTemplateLiteral(built)
    }

    override fun current(): Token = cursor.current()
    override fun peekType(offset: Int): TokenType? = cursor.peekType(offset)
    override fun isAtEnd(): Boolean = cursor.isAtEnd()
    override fun check(type: TokenType): Boolean = cursor.check(type)
    override fun advance(): Token = cursor.advance()
    override fun match(type: TokenType): Boolean = cursor.match(type)

    override fun expect(type: TokenType): Token = cursor.expect(type) { tok ->
        throw ParseException("Expected token '$type' but got '${tok.type}'", tok.line, tok.col)
    }

    override fun expectIdentifierName(): String = expect(TokenType.IDENTIFIER).text!!
}
