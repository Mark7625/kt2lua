package kt2lua.transpiler.parser

import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType
import kt2lua.transpiler.WhileStatement

/** `while (cond) { ... }`. */
class WhileFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.WHILE)

    override fun parse(ctx: ParserContext): Statement {
        ctx.expect(TokenType.WHILE)
        ctx.expect(TokenType.LPAREN)
        val cond = ctx.parseExpression()
        ctx.expect(TokenType.RPAREN)
        return WhileStatement(cond, ctx.parseBlock())
    }
}
