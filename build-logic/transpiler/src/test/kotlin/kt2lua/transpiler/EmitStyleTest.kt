package kt2lua.transpiler

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun tempPropertiesFile(contents: String): File =
    File.createTempFile("kt2lua-style-test", ".properties").apply { writeText(contents) }

class EmitStyleTest {

    @Test
    fun `missing file falls back to defaults entirely`() {
        assertEquals(EmitStyle.DEFAULT, EmitStyle.load(null))
        val missing = Files.createTempDirectory("kt2lua-style-test").toFile().resolve("missing.properties")
        assertEquals(EmitStyle.DEFAULT, EmitStyle.load(missing))
    }

    @Test
    fun `a fully-specified file is respected exactly`() {
        val file = tempPropertiesFile(
            """
            indent_size=2
            indent_style=tab
            quote_style=double
            blank_line_before_declarations=false
            """.trimIndent()
        )
        val style = EmitStyle.load(file)
        assertEquals(2, style.indentSize)
        assertEquals(IndentChar.TAB, style.indentChar)
        assertEquals("\t\t", style.indentUnit)
        assertEquals(QuoteStyle.DOUBLE, style.quoteStyle)
        assertEquals(false, style.blankLineBeforeDeclarations)
    }

    @Test
    fun `missing individual properties fall back to the default for just that one`() {
        val file = tempPropertiesFile("indent_size=5\n")
        val style = EmitStyle.load(file)
        assertEquals(5, style.indentSize)
        assertEquals(EmitStyle.DEFAULT.indentChar, style.indentChar)
        assertEquals(EmitStyle.DEFAULT.quoteStyle, style.quoteStyle)
        assertEquals(EmitStyle.DEFAULT.blankLineBeforeDeclarations, style.blankLineBeforeDeclarations)
    }

    @Test
    fun `invalid values fall back to the default for just that property`() {
        val file = tempPropertiesFile(
            """
            indent_size=not-a-number
            indent_style=sideways
            quote_style=bogus
            blank_line_before_declarations=maybe
            """.trimIndent()
        )
        assertEquals(EmitStyle.DEFAULT, EmitStyle.load(file))
    }

    @Test
    fun `a zero or negative indent size is rejected in favor of the default`() {
        val file = tempPropertiesFile("indent_size=0\n")
        assertEquals(EmitStyle.DEFAULT.indentSize, EmitStyle.load(file).indentSize)
    }

    @Test
    fun `autoDetect finds the conventional filename only when it is actually there`() {
        val dir = Files.createTempDirectory("kt2lua-style-test").toFile()
        assertNull(EmitStyle.autoDetect(dir))
        dir.resolve(EmitStyle.FILE_NAME).writeText("indent_size=4\n")
        val found = EmitStyle.autoDetect(dir)
        assertTrue(found != null && found.isFile)
        assertEquals(4, EmitStyle.load(found).indentSize)
    }

    @Test
    fun `quote_style double always escapes embedded double quotes instead of switching`() {
        val code = transpile("""fun main() { println("she said \"hi\"") }""", EmitStyle(quoteStyle = QuoteStyle.DOUBLE)).code
        assertTrue(code.contains("\"she said \\\"hi\\\"\""))
    }

    @Test
    fun `smart quoting switches to single quotes to avoid escaping`() {
        val code = transpile("""fun main() { println("she said \"hi\"") }""", EmitStyle(quoteStyle = QuoteStyle.SMART)).code
        assertTrue(code.contains("'she said \"hi\"'"))
    }

    @Test
    fun `indent size and character apply to emitted blocks`() {
        val code = transpile("fun main() { println(1) }", EmitStyle(indentSize = 1, indentChar = IndentChar.TAB)).code
        assertEquals("local function main()\n\tprint(1)\nend\nmain()\n", code)
    }

    @Test
    fun `blank lines before declarations can be disabled`() {
        val code = transpile(
            """
            fun pub() { return 1 }
            private fun priv() { return 2 }
            """.trimIndent(),
            EmitStyle(blankLineBeforeDeclarations = false)
        ).code
        assertTrue(!code.contains("\n\n"))
    }
}
