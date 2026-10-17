package kt2lua.transpiler.parser

import kt2lua.transpiler.CatchClause
import kt2lua.transpiler.ParseException
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType
import kt2lua.transpiler.TryStatement

/**
 * `try { ... } [catch (e: T) { ... }] [finally { ... }]` — see [TryStatement]'s doc comment for
 * the caveats (single catch clause, `return`-inside-try semantics). `catch`/`finally` are soft
 * keywords, recognized by their literal identifier text rather than a token type (same pattern
 * as `data`/`vararg` elsewhere in this parser).
 */
class TryFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.TRY)

    override fun parse(ctx: ParserContext): Statement {
        val tryTok = ctx.expect(TokenType.TRY)
        val tryBlock = ctx.parseBlock()
        var catch: CatchClause? = null
        if (ctx.check(TokenType.IDENTIFIER) && ctx.current().text == "catch" && ctx.peekType(1) == TokenType.LPAREN) {
            ctx.advance()
            ctx.expect(TokenType.LPAREN)
            val paramName = ctx.expectIdentifierName()
            ctx.expect(TokenType.COLON)
            ctx.parseTypeName() // the exception type: parsed and discarded, no type-based catching
            ctx.expect(TokenType.RPAREN)
            catch = CatchClause(paramName, ctx.parseBlock())
        }
        var finallyBlock: kt2lua.transpiler.Block? = null
        if (ctx.check(TokenType.IDENTIFIER) && ctx.current().text == "finally" && ctx.peekType(1) == TokenType.LBRACE) {
            ctx.advance()
            finallyBlock = ctx.parseBlock()
        }
        if (catch == null && finallyBlock == null) {
            throw ParseException("'try' requires at least one 'catch' or a 'finally' block", tryTok.line, tryTok.col)
        }
        return TryStatement(tryBlock, catch, finallyBlock)
    }
}
