package kt2lua.testrunner.differential

sealed class ComparisonOutcome {
    object Match : ComparisonOutcome()
    data class Mismatch(val detail: String) : ComparisonOutcome()
}

/**
 * Compares captured output text rather than generated Lua source — two different lowerings of the
 * same behavior must still compare equal, and a byte-identical Lua file that behaves wrongly must
 * still fail.
 */
object ResultComparator {
    fun compare(kotlin: DifferentialResult, lua: DifferentialResult): ComparisonOutcome {
        if (kotlin is DifferentialResult.Success && lua is DifferentialResult.Success) {
            return compareOutput(kotlin.output, lua.output)
        }
        if (kotlin::class == lua::class) {
            return ComparisonOutcome.Mismatch(
                "both sides failed the same way (${kotlin::class.simpleName}) but the reason could still differ - review manually:\n" +
                    "  kotlin: ${kotlin.describe()}\n  lua:    ${lua.describe()}",
            )
        }
        return ComparisonOutcome.Mismatch("kotlin: ${kotlin.describe()}\nlua:    ${lua.describe()}")
    }

    private fun compareOutput(kotlinOutputRaw: String, luaOutputRaw: String): ComparisonOutcome {
        // The JVM's println uses the platform line separator (CRLF on Windows); the Lua
        // interpreter always emits plain "\n". Normalize before comparing.
        val kotlinLines = kotlinOutputRaw.replace("\r\n", "\n").lines()
        val luaLines = luaOutputRaw.replace("\r\n", "\n").lines()
        val firstDiff = (0 until maxOf(kotlinLines.size, luaLines.size))
            .firstOrNull { i -> !linesEquivalent(kotlinLines.getOrNull(i), luaLines.getOrNull(i)) }
            ?: return ComparisonOutcome.Match
        return ComparisonOutcome.Mismatch(
            buildString {
                appendLine("output differs at line ${firstDiff + 1}:")
                appendLine("  kotlin: ${kotlinLines.getOrNull(firstDiff) ?: "<missing>"}")
                appendLine("  lua:    ${luaLines.getOrNull(firstDiff) ?: "<missing>"}")
                if (kotlinLines.size != luaLines.size) appendLine("  (kotlin had ${kotlinLines.size} lines, lua had ${luaLines.size})")
            },
        )
    }

    // Kotlin spells a missing value "null"; Lua spells it "nil" - same value, different
    // language-level token, and it can appear anywhere in a line (a bare println(null), or
    // embedded inside a formatted list/map like "[1, null, 3]"). Normalize whole-word "nil" in
    // the Lua line before comparing; \b keeps this from touching a word that merely contains
    // "nil" as a substring (there's no real risk of a *Kotlin* line containing a stray "nil" token
    // since that's not a word Kotlin's own output ever produces).
    private val NIL_WORD = Regex("""\bnil\b""")

    private fun linesEquivalent(kotlinLine: String?, luaLine: String?): Boolean =
        kotlinLine == luaLine || (kotlinLine != null && luaLine != null && kotlinLine == luaLine.replace(NIL_WORD, "null"))
}
