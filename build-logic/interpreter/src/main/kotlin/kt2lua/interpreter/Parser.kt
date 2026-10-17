package kt2lua.interpreter

private val COMPARISON_OPS = mapOf(
    TokenType.EQ to "==", TokenType.NEQ to "~=", TokenType.LT to "<",
    TokenType.GT to ">", TokenType.LTE to "<=", TokenType.GTE to ">=",
)
private val ADDITIVE_OPS = mapOf(TokenType.PLUS to "+", TokenType.MINUS to "-")
private val MULTIPLICATIVE_OPS = mapOf(
    TokenType.STAR to "*", TokenType.SLASH to "/", TokenType.PERCENT to "%",
)

/** [rightAssoc] is only set for `..` — real Lua's `..` is right-associative (`a..b..c` is
 * `a..(b..c)`), unlike every other level here. */
private class BinaryLevel(
    val matchOp: () -> String?,
    val rightAssoc: Boolean = false,
    val build: (op: String, left: Expr, right: Expr) -> Expr = { op, l, r -> Binary(op, l, r) },
)

class LuaParser(private val tokens: List<Token>) {
    private val cursor = kt2lua.common.TokenCursor(tokens, Token::type, TokenType.EOF)
    private val pos get() = cursor.pos

    fun parseChunk(): Chunk = Chunk(parseBlock(setOf(TokenType.EOF)))

    private fun parseBlock(terminators: Set<TokenType>): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!isAtEnd() && current().type !in terminators) {
            if (match(TokenType.SEMICOLON)) continue
            val stmt = parseStatement()
            stmts.add(stmt)
            if (stmt is Return) break
        }
        return stmts
    }

    private fun parseStatement(): Stmt = when {
        check(TokenType.LOCAL) -> parseLocal()
        check(TokenType.IF) -> parseIf()
        check(TokenType.FOR) -> parseFor()
        check(TokenType.WHILE) -> parseWhile()
        check(TokenType.REPEAT) -> parseRepeat()
        check(TokenType.DO) -> parseDo()
        check(TokenType.BREAK) -> {
            advance(); Break
        }
        check(TokenType.RETURN) -> parseReturn()
        check(TokenType.FUNCTION) -> parseFunctionStatement()
        else -> parseExprOrAssignStatement()
    }

    private fun parseDo(): Stmt {
        expect(TokenType.DO)
        val body = parseBlock(setOf(TokenType.END))
        expect(TokenType.END)
        return Do(body)
    }

    private fun parseExprOrAssignStatement(): Stmt {
        val first = parseSuffixedExpr()
        if (check(TokenType.COMMA) || check(TokenType.ASSIGN)) {
            val targets = mutableListOf(exprToAssignTarget(first))
            while (match(TokenType.COMMA)) targets.add(exprToAssignTarget(parseSuffixedExpr()))
            expect(TokenType.ASSIGN)
            return Assign(targets, parseExprList())
        }
        return ExprStmt(first)
    }

    private fun exprToAssignTarget(expr: Expr): AssignTarget = when (expr) {
        is VarRef -> NameTarget(expr.name)
        is IndexExpr -> IndexTarget(expr.obj, expr.key)
        else -> throw LuaParseException("Cannot assign to this expression", current().line)
    }

    private fun parseLocal(): Stmt {
        expect(TokenType.LOCAL)
        if (match(TokenType.FUNCTION)) {
            val name = expectIdentifierName()
            val (params, isVararg, body) = parseFuncBody()
            return LocalFunctionDecl(name, params, isVararg, body)
        }
        val names = mutableListOf(expectIdentifierName())
        while (match(TokenType.COMMA)) names.add(expectIdentifierName())
        val exprs = if (match(TokenType.ASSIGN)) parseExprList() else emptyList()
        return LocalDecl(names, exprs)
    }

    /** `function Name(.Name)*(:Name)? (...) body end`, desugared to a plain [Assign]. */
    private fun parseFunctionStatement(): Stmt {
        expect(TokenType.FUNCTION)
        var target: Expr = VarRef(expectIdentifierName())
        while (match(TokenType.DOT)) target = IndexExpr(target, StringLit(expectIdentifierName()))
        var isMethod = false
        if (match(TokenType.COLON)) {
            target = IndexExpr(target, StringLit(expectIdentifierName()))
            isMethod = true
        }
        val (params, isVararg, body) = parseFuncBody()
        val finalParams = if (isMethod) listOf("self") + params else params
        return Assign(listOf(exprToAssignTarget(target)), listOf(FunctionExpr(finalParams, isVararg, body)))
    }

    private data class FuncBody(val params: List<String>, val isVararg: Boolean, val body: List<Stmt>)

    private fun parseFuncBody(): FuncBody {
        expect(TokenType.LPAREN)
        val params = mutableListOf<String>()
        var isVararg = false
        if (!check(TokenType.RPAREN)) {
            do {
                if (match(TokenType.ELLIPSIS)) {
                    isVararg = true
                    break
                }
                params.add(expectIdentifierName())
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN)
        val body = parseBlock(setOf(TokenType.END))
        expect(TokenType.END)
        return FuncBody(params, isVararg, body)
    }

    private fun parseIf(): Stmt {
        expect(TokenType.IF)
        val cond = parseExpr()
        expect(TokenType.THEN)
        val thenBody = parseBlock(setOf(TokenType.ELSE, TokenType.ELSEIF, TokenType.END))
        return If(cond, thenBody, parseIfTail())
    }

    private fun parseIfTail(): List<Stmt>? = when {
        match(TokenType.ELSEIF) -> {
            val cond = parseExpr()
            expect(TokenType.THEN)
            val body = parseBlock(setOf(TokenType.ELSE, TokenType.ELSEIF, TokenType.END))
            listOf(If(cond, body, parseIfTail()))
        }
        match(TokenType.ELSE) -> {
            val body = parseBlock(setOf(TokenType.END))
            expect(TokenType.END)
            body
        }
        else -> {
            expect(TokenType.END)
            null
        }
    }

    /** Disambiguates numeric `for i = ...` from generic `for k, v in ...` after the first name. */
    private fun parseFor(): Stmt {
        expect(TokenType.FOR)
        val firstName = expectIdentifierName()
        if (check(TokenType.ASSIGN)) {
            advance()
            val start = parseExpr()
            expect(TokenType.COMMA)
            val limit = parseExpr()
            val step = if (match(TokenType.COMMA)) parseExpr() else null
            expect(TokenType.DO)
            val body = parseBlock(setOf(TokenType.END))
            expect(TokenType.END)
            return NumericFor(firstName, start, limit, step, body)
        }
        val names = mutableListOf(firstName)
        while (match(TokenType.COMMA)) names.add(expectIdentifierName())
        expect(TokenType.IN)
        val exprs = parseExprList()
        expect(TokenType.DO)
        val body = parseBlock(setOf(TokenType.END))
        expect(TokenType.END)
        return GenericFor(names, exprs, body)
    }

    private fun parseWhile(): Stmt {
        expect(TokenType.WHILE)
        val cond = parseExpr()
        expect(TokenType.DO)
        val body = parseBlock(setOf(TokenType.END))
        expect(TokenType.END)
        return While(cond, body)
    }

    private fun parseRepeat(): Stmt {
        expect(TokenType.REPEAT)
        val body = parseBlock(setOf(TokenType.UNTIL))
        expect(TokenType.UNTIL)
        return Repeat(body, parseExpr())
    }

    private fun parseReturn(): Stmt {
        expect(TokenType.RETURN)
        if (!canStartExpr()) {
            match(TokenType.SEMICOLON)
            return Return(emptyList())
        }
        val exprs = parseExprList()
        match(TokenType.SEMICOLON)
        return Return(exprs)
    }

    private fun canStartExpr(): Boolean = when (current().type) {
        TokenType.NUMBER, TokenType.STRING, TokenType.TRUE, TokenType.FALSE, TokenType.NIL, TokenType.ELLIPSIS,
        TokenType.IDENTIFIER, TokenType.LPAREN, TokenType.MINUS, TokenType.NOT, TokenType.HASH,
        TokenType.FUNCTION, TokenType.LBRACE -> true
        else -> false
    }

    private fun parseExprList(): List<Expr> {
        val exprs = mutableListOf(parseExpr())
        while (match(TokenType.COMMA)) exprs.add(parseExpr())
        return exprs
    }

    private fun matchTokenOp(ops: Map<TokenType, String>): String? {
        val op = ops[current().type] ?: return null
        advance()
        return op
    }

    /** Loosest-to-tightest: `or`, `and`, `==`/`~=`/`<`/`>`/`<=`/`>=`, `..`, `+`/`-`, `*`/`/`/`%`.
     * [parsePower] and tighter stay their own functions since `^`'s right-associativity
     * deliberately interleaves with unary (`2^-2` is valid Lua), which doesn't fit this uniform
     * per-level shape. */
    private val binaryLevels: List<BinaryLevel> = listOf(
        BinaryLevel({ if (match(TokenType.OR)) "or" else null }),
        BinaryLevel({ if (match(TokenType.AND)) "and" else null }),
        BinaryLevel({ matchTokenOp(COMPARISON_OPS) }),
        BinaryLevel({ if (match(TokenType.DOT_DOT)) ".." else null }, rightAssoc = true),
        BinaryLevel({ matchTokenOp(ADDITIVE_OPS) }),
        BinaryLevel({ matchTokenOp(MULTIPLICATIVE_OPS) }),
    )

    private fun parseExpr(): Expr = parseBinary(0)

    /** Left-associative levels loop, combining leftward; the right-associative level (`..`)
     * instead recurses back into itself for its right operand, so a chain nests to the right. */
    private fun parseBinary(level: Int): Expr {
        if (level >= binaryLevels.size) return parseUnary()
        val binLevel = binaryLevels[level]
        val left = parseBinary(level + 1)
        if (binLevel.rightAssoc) {
            val op = binLevel.matchOp() ?: return left
            return binLevel.build(op, left, parseBinary(level))
        }
        var result = left
        while (true) {
            val op = binLevel.matchOp() ?: break
            result = binLevel.build(op, result, parseBinary(level + 1))
        }
        return result
    }

    private fun parseUnary(): Expr = when {
        match(TokenType.MINUS) -> Unary("-", parseUnary())
        match(TokenType.NOT) -> Unary("not", parseUnary())
        match(TokenType.HASH) -> Unary("#", parseUnary())
        else -> parsePower()
    }

    private fun parsePower(): Expr {
        val base = parseSuffixedExpr()
        if (match(TokenType.CARET)) return Binary("^", base, parseUnary())
        return base
    }

    private fun parseSuffixedExpr(): Expr {
        var expr = parsePrimaryAtom()
        while (true) {
            expr = when {
                check(TokenType.DOT) -> {
                    advance()
                    IndexExpr(expr, StringLit(expectIdentifierName()))
                }
                check(TokenType.LBRACKET) -> {
                    advance()
                    val key = parseExpr()
                    expect(TokenType.RBRACKET)
                    IndexExpr(expr, key)
                }
                check(TokenType.COLON) -> {
                    advance()
                    val methodName = expectIdentifierName()
                    MethodCall(expr, methodName, parseCallArgs())
                }
                check(TokenType.LPAREN) -> Call(expr, parseCallArgs())
                else -> return expr
            }
        }
    }

    private fun parseCallArgs(): List<Expr> {
        expect(TokenType.LPAREN)
        val args = mutableListOf<Expr>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpr())
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN)
        return args
    }

    private fun parsePrimaryAtom(): Expr {
        val tok = current()
        return when (tok.type) {
            TokenType.NUMBER -> {
                advance(); NumberLit(tok.numberValue!!)
            }
            TokenType.STRING -> {
                advance(); StringLit(tok.text!!)
            }
            TokenType.TRUE -> {
                advance(); BoolLit(true)
            }
            TokenType.FALSE -> {
                advance(); BoolLit(false)
            }
            TokenType.NIL -> {
                advance(); NilLit
            }
            TokenType.ELLIPSIS -> {
                advance(); Vararg
            }
            TokenType.IDENTIFIER -> {
                advance(); VarRef(tok.text!!)
            }
            TokenType.LPAREN -> {
                advance()
                val e = parseExpr()
                expect(TokenType.RPAREN)
                Paren(e)
            }
            TokenType.FUNCTION -> {
                advance()
                val (params, isVararg, body) = parseFuncBody()
                FunctionExpr(params, isVararg, body)
            }
            TokenType.LBRACE -> parseTableCtor()
            else -> throw LuaParseException("Unexpected token '${tok.type}'", tok.line)
        }
    }

    private fun parseTableCtor(): Expr {
        expect(TokenType.LBRACE)
        val fields = mutableListOf<TableField>()
        while (!check(TokenType.RBRACE)) {
            fields.add(parseTableField())
            if (!match(TokenType.COMMA) && !match(TokenType.SEMICOLON)) break
        }
        expect(TokenType.RBRACE)
        return TableCtor(fields)
    }

    private fun parseTableField(): TableField {
        if (check(TokenType.LBRACKET)) {
            advance()
            val key = parseExpr()
            expect(TokenType.RBRACKET)
            expect(TokenType.ASSIGN)
            return KeyedField(key, parseExpr())
        }
        if (check(TokenType.IDENTIFIER) && peekType(1) == TokenType.ASSIGN) {
            val name = expectIdentifierName()
            expect(TokenType.ASSIGN)
            return NamedField(name, parseExpr())
        }
        return PositionalField(parseExpr())
    }

    private fun current(): Token = cursor.current()
    private fun peekType(offset: Int): TokenType? = cursor.peekType(offset)
    private fun isAtEnd(): Boolean = cursor.isAtEnd()
    private fun check(type: TokenType): Boolean = cursor.check(type)
    private fun advance(): Token = cursor.advance()
    private fun match(type: TokenType): Boolean = cursor.match(type)

    private fun expect(type: TokenType): Token = cursor.expect(type) { tok ->
        throw LuaParseException("Expected token '$type' but got '${tok.type}'", tok.line)
    }

    private fun expectIdentifierName(): String = expect(TokenType.IDENTIFIER).text!!
}
