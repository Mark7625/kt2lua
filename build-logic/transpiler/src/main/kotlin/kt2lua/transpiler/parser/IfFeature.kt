package kt2lua.transpiler.parser

import kt2lua.transpiler.Block
import kt2lua.transpiler.Expression
import kt2lua.transpiler.IfExpr
import kt2lua.transpiler.IfStatement
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType

/** `if`/`else` — both the statement form (dispatched via [StatementParser]) and the expression
 * form ([parseIfExpr], called directly from the central parser's atom parsing since an
 * expression-position `if` is never reached through statement dispatch). */
class IfFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.IF)

    override fun parse(ctx: ParserContext): Statement = parseIfStatement(ctx)

    private fun parseIfStatement(ctx: ParserContext): IfStatement {
        ctx.expect(TokenType.IF)
        ctx.expect(TokenType.LPAREN)
        val cond = ctx.parseExpression()
        ctx.expect(TokenType.RPAREN)
        val thenBranch = ctx.parseBranch()
        var elseBranch: Block? = null
        if (ctx.match(TokenType.ELSE)) {
            elseBranch = if (ctx.check(TokenType.IF)) Block(listOf(parseIfStatement(ctx))) else ctx.parseBranch()
        }
        return IfStatement(cond, thenBranch, elseBranch)
    }

    fun parseIfExpr(ctx: ParserContext): Expression {
        ctx.expect(TokenType.IF)
        ctx.expect(TokenType.LPAREN)
        val cond = ctx.parseExpression()
        ctx.expect(TokenType.RPAREN)
        val thenExpr = ctx.parseExpressionBranch()
        ctx.expect(TokenType.ELSE)
        val elseExpr = ctx.parseExpressionBranch()
        return IfExpr(cond, thenExpr, elseExpr)
    }
}
