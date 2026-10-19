package kt2lua.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CompatIssueTest {
    private val originalOut = System.out
    private val captured = ByteArrayOutputStream()

    @BeforeTest
    fun redirectStdout() {
        System.setOut(PrintStream(captured))
    }

    @AfterTest
    fun restoreStdout() {
        System.setOut(originalOut)
    }

    @Test
    fun `an unambiguous unsupported declaration produces a pre-filled GitHub issue URL`() {
        runCompatCommand(arrayOf("compat", "issue", "AbstractDoubleTimeSource"))
        val output = captured.toString()
        assertTrue(output.contains("Support kotlin.time.AbstractDoubleTimeSource"))
        assertTrue(output.contains("https://github.com/Mark7625/kt2lua/issues/new?"))
        assertTrue(output.contains("title=Support+kotlin.time.AbstractDoubleTimeSource"))
        assertTrue(output.contains("labels=compatibility"))
    }

    @Test
    fun `an already-supported declaration reports there is nothing to file`() {
        runCompatCommand(arrayOf("compat", "issue", "kotlin.collections.listOf(T)"))
        assertTrue(captured.toString().contains("already fully supported"))
    }
}
