package kt2lua.transpiler.parser

import kt2lua.transpiler.ObjectDecl
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType

/** `object { ... }` — see [ObjectDecl]'s doc comment for the Lua shape this produces. */
class ObjectFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = ctx.check(TokenType.OBJECT)

    override fun parse(ctx: ParserContext): Statement {
        ctx.expect(TokenType.OBJECT)
        val name = ctx.expectIdentifierName()
        ctx.expect(TokenType.LBRACE)
        val body = mutableListOf<Statement>()
        while (!ctx.check(TokenType.RBRACE)) {
            if (ctx.match(TokenType.SEMICOLON)) continue
            body.add(ctx.parseStatement())
        }
        ctx.expect(TokenType.RBRACE)
        return ObjectDecl(name, body)
    }
}
