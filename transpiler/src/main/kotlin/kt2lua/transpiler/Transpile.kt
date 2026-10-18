package kt2lua.transpiler

data class TranspileResult(val ast: Program, val code: String)

fun transpile(source: String, style: EmitStyle = EmitStyle.DEFAULT, literalConstants: Map<String, String> = emptyMap(), bindings: LuaBindings = LuaBindings()): TranspileResult {
    val tokens = Lexer(source).tokenize()
    val scopedBindings = bindings.forSource(source)
    val ast = Parser(tokens, literalConstants, scopedBindings).parseProgram()
    val code = Emitter(style, scopedBindings).emitProgram(ast)
    return TranspileResult(ast, code)
}
