package kt2lua.transpiler

import java.io.File
import java.util.Properties

enum class IndentChar { SPACE, TAB }

/** `SMART` double-quotes by default, switching to single quotes only when the text contains a
 * `"` but no `'` (avoids escaping). `DOUBLE`/`SINGLE` always use that quote character. */
enum class QuoteStyle { SMART, DOUBLE, SINGLE }

/** Formatting-only knobs for [Emitter]'s Lua output — never load-bearing for correctness. A
 * project can override any subset of these via a `kt2lua-style.properties` file; see [load]. */
data class EmitStyle(
    val indentSize: Int = 3,
    val indentChar: IndentChar = IndentChar.SPACE,
    val quoteStyle: QuoteStyle = QuoteStyle.SMART,
    val blankLineBeforeDeclarations: Boolean = true,
) {
    val indentUnit: String = (if (indentChar == IndentChar.TAB) "\t" else " ").repeat(indentSize)

    companion object {
        val DEFAULT = EmitStyle()
        const val FILE_NAME = "kt2lua-style.properties"

        /** [file] missing/unreadable/null falls back to [DEFAULT] entirely. A file that exists
         * but only sets some properties (or sets one to an unparseable value) falls back to
         * [DEFAULT]'s value for each individual property it doesn't successfully set, rather
         * than discarding the whole file over one bad entry. */
        fun load(file: File?): EmitStyle {
            if (file == null || !file.isFile) return DEFAULT
            val props = Properties()
            try {
                file.inputStream().use { props.load(it) }
            } catch (e: Exception) {
                return DEFAULT
            }
            return EmitStyle(
                indentSize = props.getProperty("indent_size")?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT.indentSize,
                indentChar = enumValueOrDefault(props.getProperty("indent_style"), DEFAULT.indentChar),
                quoteStyle = enumValueOrDefault(props.getProperty("quote_style"), DEFAULT.quoteStyle),
                blankLineBeforeDeclarations = props.getProperty("blank_line_before_declarations")?.trim()?.toBooleanStrictOrNull()
                    ?: DEFAULT.blankLineBeforeDeclarations,
            )
        }

        fun autoDetect(dir: File): File? = File(dir, FILE_NAME).takeIf { it.isFile }

        private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
            if (raw == null) return default
            return enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: default
        }
    }
}
