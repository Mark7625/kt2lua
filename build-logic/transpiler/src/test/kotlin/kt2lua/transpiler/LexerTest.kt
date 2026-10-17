package kt2lua.transpiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LexerTest {

    private fun types(source: String): List<TokenType> = Lexer(source).tokenize().map { it.type }

    @Test
    fun `tokenizes keywords distinctly from identifiers`() {
        assertEquals(
            listOf(TokenType.FUN, TokenType.IDENTIFIER, TokenType.LPAREN, TokenType.RPAREN, TokenType.EOF),
            types("fun funny()")
        )
    }

    @Test
    fun `tokenizes int and double literals distinctly`() {
        val tokens = Lexer("42 3.14").tokenize()
        assertEquals(42, tokens[0].intValue)
        assertEquals(TokenType.INT_LITERAL, tokens[0].type)
        assertEquals(3.14, tokens[1].doubleValue)
        assertEquals(TokenType.DOUBLE_LITERAL, tokens[1].type)
    }

    @Test
    fun `tokenizes two-char operators greedily before one-char`() {
        assertEquals(
            listOf(TokenType.EQ, TokenType.NEQ, TokenType.LTE, TokenType.GTE, TokenType.AND_AND, TokenType.OR_OR, TokenType.EOF),
            types("== != <= >= && ||")
        )
    }

    @Test
    fun `distinguishes dot dot from dot`() {
        assertEquals(listOf(TokenType.DOT_DOT, TokenType.DOT, TokenType.EOF), types(".. ."))
    }

    @Test
    fun `line comments are skipped`() {
        assertEquals(listOf(TokenType.FUN, TokenType.EOF), types("fun // trailing comment\n"))
    }

    @Test
    fun `block comments are skipped`() {
        assertEquals(listOf(TokenType.FUN, TokenType.VAL, TokenType.EOF), types("fun /* mid \n comment */ val"))
    }

    @Test
    fun `string templates split text and expression parts`() {
        val token = Lexer("\"hello \$name!\"").tokenize()[0]
        assertEquals(TokenType.STRING_TEMPLATE, token.type)
        val parts = token.templateParts!!
        assertEquals(TemplatePart.Text("hello "), parts[0])
        assertEquals(TemplatePart.ExprSource("name"), parts[1])
        assertEquals(TemplatePart.Text("!"), parts[2])
    }

    @Test
    fun `string template handles brace expressions`() {
        val token = Lexer("\"total: \${a + b}\"").tokenize()[0]
        val parts = token.templateParts!!
        assertEquals(TemplatePart.Text("total: "), parts[0])
        assertEquals(TemplatePart.ExprSource("a + b"), parts[1])
    }

    @Test
    fun `unterminated string raises a lex exception with position`() {
        val ex = assertFailsWith<LexException> { Lexer("\"oops").tokenize() }
        assertEquals(1, ex.line)
    }

    @Test
    fun `unexpected character raises a lex exception`() {
        assertFailsWith<LexException> { Lexer("@").tokenize() }
    }

    @Test
    fun `tracks line numbers across newlines`() {
        val tokens = Lexer("val a = 1\nval b = 2").tokenize()
        val secondVal = tokens.first { it.type == TokenType.VAL && it != tokens[0] }
        assertEquals(2, secondVal.line)
    }

    @Test
    fun `const is its own keyword token`() {
        assertEquals(listOf(TokenType.CONST, TokenType.VAL, TokenType.EOF), types("const val"))
    }

    @Test
    fun `unimplemented hard keywords lex to a dedicated unsupported token`() {
        assertEquals(TokenType.UNSUPPORTED_KEYWORD, types("interface").single { it != TokenType.EOF })
    }

    @Test
    fun `soft keywords like init lex as plain identifiers`() {
        assertEquals(TokenType.IDENTIFIER, types("init").single { it != TokenType.EOF })
    }

    @Test
    fun `modifier keywords like open, enum, sealed lex as plain identifiers too`() {
        // Regression test: these were briefly hard-rejected, which broke real code calling a
        // method literally named open() — real Kotlin only reserves these as modifiers
        // immediately before a declaration (e.g. `open class`), not everywhere.
        for (word in listOf("open", "enum", "sealed", "abstract", "override", "companion", "internal", "protected", "operator", "infix", "inline", "suspend", "lateinit", "annotation")) {
            assertEquals(TokenType.IDENTIFIER, types(word).single { it != TokenType.EOF }, "'$word' should lex as a plain identifier")
        }
    }
}
