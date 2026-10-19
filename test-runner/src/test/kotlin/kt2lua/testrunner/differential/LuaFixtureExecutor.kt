package kt2lua.testrunner.differential

import kt2lua.interpreter.LuaLexException
import kt2lua.interpreter.LuaParseException
import kt2lua.interpreter.LuaRuntimeException
import kt2lua.interpreter.interpret
import kt2lua.transpiler.transpile

data class LuaRun(val result: DifferentialResult, val generatedLua: String)

/**
 * Transpiles a fixture and runs the generated Lua through kt2lua's own interpreter, in-process —
 * there's no separate Lua binary or process to launch, the interpreter already lives on the JVM.
 */
object LuaFixtureExecutor {
    fun run(kotlinSource: String): LuaRun {
        val transpiled = try {
            transpile(kotlinSource)
        } catch (e: Exception) {
            return LuaRun(DifferentialResult.CompileFailure("kt2lua transpile failure: ${e.message}"), "")
        }
        val out = StringBuilder()
        val result = try {
            interpret(transpiled.code, onOutput = { out.append(it) })
            DifferentialResult.Success(out.toString())
        } catch (e: LuaLexException) {
            DifferentialResult.CompileFailure("Lua lex error: ${e.message}")
        } catch (e: LuaParseException) {
            DifferentialResult.CompileFailure("Lua parse error: ${e.message}")
        } catch (e: LuaRuntimeException) {
            DifferentialResult.RuntimeFailure("LuaRuntimeException", e.message)
        } catch (e: Exception) {
            DifferentialResult.RuntimeFailure(e::class.qualifiedName ?: e::class.java.name, e.message)
        }
        return LuaRun(result, transpiled.code)
    }
}
