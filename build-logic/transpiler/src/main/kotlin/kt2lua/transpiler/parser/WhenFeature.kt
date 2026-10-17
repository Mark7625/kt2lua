package kt2lua.transpiler.parser

import kt2lua.transpiler.Block
import kt2lua.transpiler.Expression
import kt2lua.transpiler.InRangeCondition
import kt2lua.transpiler.IsTypeCondition
import kt2lua.transpiler.ParseException
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType
import kt2lua.transpiler.TypeName
import kt2lua.transpiler.ValueCondition
import kt2lua.transpiler.WhenBranch
import kt2lua.transpiler.WhenCondition
import kt2lua.transpiler.WhenExpr
import kt2lua.transpiler.WhenExprBranch
import kt2lua.transpiler.WhenStatement

private val IS_TYPE_WHITELIST = setOf(TypeName.INT, TypeName.DOUBLE, TypeName.STRING, TypeName.BOOLEAN)

/** `when` — both the statement form (dispatched via [StatementParser]) and the expression form
 * ([parseWhenExpr], called directly from the central parser's atom parsing). Both forms share
 * their subject/condition-parsing grammar almost verbatim, so this one class owns all of it. */
class WhenFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.WHEN)

    override fun parse(ctx: ParserContext): Statement = parseWhenStatement(ctx)

    /** `when (subject)` or subject-less `when`; returns null for the latter. */
    private fun parseWhenSubject(ctx: ParserContext): Expression? {
        if (!ctx.match(TokenType.LPAREN)) return null
        val subject = ctx.parseExpression()
        ctx.expect(TokenType.RPAREN)
        return subject
    }

    private fun parseWhenCondition(ctx: ParserContext, hasSubject: Boolean): WhenCondition {
        if (ctx.check(TokenType.IN)) {
            val inTok = ctx.advance()
            if (!hasSubject) throw ParseException("'in' when-condition requires a when subject, e.g. when (x)", inTok.line, inTok.col)
            val start = ctx.parseAdditive()
            val inclusive = when {
                ctx.match(TokenType.DOT_DOT) -> true
                ctx.match(TokenType.UNTIL) -> false
                else -> throw ParseException("Expected '..' or 'until' after 'in'", ctx.current().line, ctx.current().col)
            }
            return InRangeCondition(start, ctx.parseAdditive(), inclusive)
        }
        if (ctx.check(TokenType.IS)) {
            val isTok = ctx.advance()
            if (!hasSubject) throw ParseException("'is' when-condition requires a when subject, e.g. when (x)", isTok.line, isTok.col)
            val typeTok = ctx.current()
            val type = ctx.parseTypeName()
            if (type !in IS_TYPE_WHITELIST) {
                throw ParseException(
                    "'is ${typeTok.text}' is not supported — this subset only checks 'is' against Int/Double/String/Boolean (no class/RTTI support)",
                    typeTok.line,
                    typeTok.col,
                )
            }
            return IsTypeCondition(type)
        }
        return ValueCondition(ctx.parseExpression())
    }

    private fun parseWhenConditions(ctx: ParserContext, hasSubject: Boolean): List<WhenCondition> {
        val conditions = mutableListOf(parseWhenCondition(ctx, hasSubject))
        while (ctx.match(TokenType.COMMA)) conditions.add(parseWhenCondition(ctx, hasSubject))
        return conditions
    }

    private fun parseWhenStatement(ctx: ParserContext): WhenStatement {
        ctx.expect(TokenType.WHEN)
        val subject = parseWhenSubject(ctx)
        ctx.expect(TokenType.LBRACE)
        val branches = mutableListOf<WhenBranch>()
        var elseBranch: Block? = null
        while (!ctx.check(TokenType.RBRACE)) {
            if (ctx.match(TokenType.ELSE)) {
                ctx.expect(TokenType.ARROW)
                elseBranch = ctx.parseBranch()
            } else {
                val conditions = parseWhenConditions(ctx, subject != null)
                ctx.expect(TokenType.ARROW)
                branches.add(WhenBranch(conditions, ctx.parseBranch()))
            }
        }
        ctx.expect(TokenType.RBRACE)
        return WhenStatement(subject, branches, elseBranch)
    }

    /** Unlike [parseWhenStatement], an `else` branch is mandatory — there's no exhaustiveness
     * checking in this subset, so it's the only way to guarantee the expression has a value. */
    fun parseWhenExpr(ctx: ParserContext): WhenExpr {
        val whenTok = ctx.expect(TokenType.WHEN)
        val subject = parseWhenSubject(ctx)
        ctx.expect(TokenType.LBRACE)
        val branches = mutableListOf<WhenExprBranch>()
        var elseResult: Expression? = null
        while (!ctx.check(TokenType.RBRACE)) {
            if (ctx.match(TokenType.ELSE)) {
                ctx.expect(TokenType.ARROW)
                elseResult = ctx.parseExpressionBranch()
            } else {
                val conditions = parseWhenConditions(ctx, subject != null)
                ctx.expect(TokenType.ARROW)
                branches.add(WhenExprBranch(conditions, ctx.parseExpressionBranch()))
            }
        }
        ctx.expect(TokenType.RBRACE)
        val finalElse = elseResult ?: throw ParseException(
            "when-expression requires an 'else' branch in this subset (no exhaustiveness checking)",
            whenTok.line,
            whenTok.col,
        )
        return WhenExpr(subject, branches, finalElse)
    }
}
