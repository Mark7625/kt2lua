package kt2lua.transpiler

data class TranspileResult(val ast: Program, val code: String)

fun transpile(source: String, style: EmitStyle = EmitStyle.DEFAULT): TranspileResult {
    val tokens = Lexer(source).tokenize()
    val ast = Parser(tokens).parseProgram()
    val code = Emitter(style).emitProgram(ast)
    return TranspileResult(ast, code)
}
