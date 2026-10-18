package kt2lua.transpiler.parser

import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.ThrowStatement
import kt2lua.transpiler.TokenType

/** `throw expr` — compiles to Lua's `error(...)`; see [ThrowStatement]'s doc comment. */
class ThrowFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.THROW)

    override fun parse(ctx: ParserContext): Statement {
        ctx.expect(TokenType.THROW)
        return ThrowStatement(ctx.parseExpression())
    }
}
