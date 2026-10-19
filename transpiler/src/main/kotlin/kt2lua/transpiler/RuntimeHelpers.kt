package kt2lua.transpiler

/** Lua snippets prepended to generated code when a feature needs them. Each one lives in its own
 * file under `resources/runtime/`; leading `--` comment lines there document the helper and are
 * not emitted. */
enum class RuntimeHelper(resource: String) {
    RESULT("result"),
    RECORD_COPY("record_copy"),
    TAKE_IF("take_if"),
    INT_DIV("int_div"),
    MOD("mod"),
    NULL_SENTINEL("null_sentinel"),
    FMT_VALUE("fmt_value"),
    FMT_DOUBLE("fmt_double"),
    TO_INT("to_int"),
    EXCEPTION("exception"),
    MATH_FLOAT("math_float"),
    MATH_PRECISE("math_precise"),
    RANDOM("random");

    val source: String = loadRuntimeSource(resource)
}

private fun loadRuntimeSource(name: String): String {
    val raw = RuntimeHelper::class.java.getResourceAsStream("/runtime/$name.lua")
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: error("Missing runtime helper /runtime/$name.lua")
    val body = raw.replace("\r\n", "\n").lines().dropWhile { it.startsWith("--") || it.isBlank() }.joinToString("\n")
    return body.trimEnd() + "\n\n"
}
