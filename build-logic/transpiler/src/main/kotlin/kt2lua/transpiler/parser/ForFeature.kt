package kt2lua.transpiler.parser

import kt2lua.transpiler.ForInStatement
import kt2lua.transpiler.ForStatement
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType

/** `for (i in a..b)` / `for (i in a until b)` (a numeric range) vs `for (x in someList)`
 * (collection iteration) — disambiguated after the first name by whether `..`/`until` follows. */
class ForFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.FOR)

    override fun parse(ctx: ParserContext): Statement {
        ctx.expect(TokenType.FOR)
        ctx.expect(TokenType.LPAREN)
        val varName = ctx.expectIdentifierName()
        ctx.expect(TokenType.IN)
        val start = ctx.parseAdditive()
        val inclusive = when {
            ctx.match(TokenType.DOT_DOT) -> true
            ctx.match(TokenType.UNTIL) -> false
            else -> {
                ctx.expect(TokenType.RPAREN)
                return ForInStatement(varName, start, ctx.parseBlock())
            }
        }
        val end = ctx.parseAdditive()
        val step = if (ctx.match(TokenType.STEP)) ctx.parseAdditive() else null
        ctx.expect(TokenType.RPAREN)
        return ForStatement(varName, start, end, inclusive, step, ctx.parseBlock())
    }
}
