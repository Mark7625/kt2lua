package kt2lua.transpiler.parser

import kt2lua.transpiler.ClassDecl
import kt2lua.transpiler.ClassParam
import kt2lua.transpiler.CallArg
import kt2lua.transpiler.ParseException
import kt2lua.transpiler.ParserContext
import kt2lua.transpiler.Statement
import kt2lua.transpiler.StatementParser
import kt2lua.transpiler.TokenType

private val SOFT_CLASS_PREFIXES = setOf("open", "data")
private val UNSUPPORTED_CLASS_MODIFIERS = setOf("enum", "sealed", "abstract")

/**
 * `[open|data] class Name(...) [: Super(args)] { ... }` — see [ClassDecl]'s doc comment for what
 * this subset supports. `open`/`data` (and the unsupported `enum`/`sealed`/`abstract`) are soft
 * keywords, only modifiers immediately before `class`, plain identifiers everywhere else.
 */
class ClassFeature : StatementParser {
    override fun supports(ctx: ParserContext): Boolean = when {
        ctx.check(TokenType.CLASS) -> true
        ctx.check(TokenType.IDENTIFIER) && ctx.peekType(1) == TokenType.CLASS ->
            ctx.current().text in SOFT_CLASS_PREFIXES || ctx.current().text in UNSUPPORTED_CLASS_MODIFIERS
        else -> false
    }

    override fun parse(ctx: ParserContext): Statement {
        if (ctx.check(TokenType.CLASS)) return parseClassDecl(ctx, isData = false)
        // Thrown before advancing, so an unsupported-modifier error still points at it.
        val modifier = ctx.current().text
        if (modifier in UNSUPPORTED_CLASS_MODIFIERS) throw ctx.unsupportedKeywordError()
        ctx.advance()
        return parseClassDecl(ctx, isData = modifier == "data")
    }

    private fun skipOpenOverrideModifiers(ctx: ParserContext) {
        while (ctx.check(TokenType.IDENTIFIER) && ctx.current().text in setOf("open", "override")) ctx.advance()
    }

    private fun parseClassDecl(ctx: ParserContext, isData: Boolean): ClassDecl {
        ctx.expect(TokenType.CLASS)
        val name = ctx.expectIdentifierName()
        val params = mutableListOf<ClassParam>()
        if (ctx.match(TokenType.LPAREN)) {
            if (!ctx.check(TokenType.RPAREN)) {
                do {
                    val isVar = when {
                        ctx.match(TokenType.VAR) -> true
                        ctx.match(TokenType.VAL) -> false
                        else -> throw ParseException(
                            "Primary constructor parameters must be 'val' or 'var' in this subset (a plain, non-property parameter isn't supported)",
                            ctx.current().line,
                            ctx.current().col,
                        )
                    }
                    val pname = ctx.expectIdentifierName()
                    ctx.expect(TokenType.COLON)
                    params.add(ClassParam(pname, ctx.parseTypeName(), isVar))
                } while (ctx.match(TokenType.COMMA))
            }
            ctx.expect(TokenType.RPAREN)
        }
        var superClass: String? = null
        var superArgs: List<CallArg> = emptyList()
        if (ctx.match(TokenType.COLON)) {
            superClass = ctx.expectIdentifierName()
            if (ctx.check(TokenType.LPAREN)) superArgs = ctx.parseCallArgs()
        }
        val methods = mutableListOf<kt2lua.transpiler.FunctionDecl>()
        val properties = mutableListOf<kt2lua.transpiler.VariableDecl>()
        if (ctx.match(TokenType.LBRACE)) {
            while (!ctx.check(TokenType.RBRACE)) {
                skipOpenOverrideModifiers(ctx)
                val isPrivate = ctx.match(TokenType.PRIVATE)
                when {
                    ctx.check(TokenType.FUN) -> methods.add(ctx.parseFunctionDecl(isPrivate))
                    ctx.check(TokenType.VAL) || ctx.check(TokenType.VAR) -> properties.add(ctx.parseVariableDecl(isPrivate))
                    else -> throw ParseException(
                        "Only 'fun'/'val'/'var' members are supported in a class body in this subset",
                        ctx.current().line,
                        ctx.current().col,
                    )
                }
            }
            ctx.expect(TokenType.RBRACE)
        }
        return ClassDecl(name, params, methods, isData, superClass, superArgs, properties)
    }
}
