package kt2lua.testrunner.differential

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.createTempDirectory

/**
 * Compiles Kotlin fixtures with the real Kotlin compiler (in-process, via the
 * kotlin-compiler-embeddable API — the same one Gradle's own Kotlin plugin drives) and runs them
 * reflectively on this JVM. One compiler invocation handles a whole batch of fixtures: starting
 * the compiler has a multi-second fixed cost, so compiling fixtures one at a time would dominate
 * the whole suite's runtime.
 */
class KotlinBatchExecutor(private val timeoutMs: Long = 10_000) : AutoCloseable {
    private val workDir = createTempDirectory("kt2lua-differential").toFile()
    private val outDir = File(workDir, "classes").apply { mkdirs() }
    private val runnerExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "kotlin-fixture-runner").apply { isDaemon = true } }

    /** Compiles [cases] together and returns each case's facade class name and output, if compiled. */
    fun run(cases: List<DifferentialTestCase>): Map<String, Result> {
        if (cases.isEmpty()) return emptyMap()
        val facades = cases.mapIndexed { i, case -> case.id to facadeNameFor(case.id, i) }.toMap()
        val compiled = compileBatch(cases, facades)
        if (!compiled.success) {
            // Isolate which fixture(s) actually fail to compile by falling back to one-at-a-time.
            return cases.associate { case -> case.id to compileAndRunOne(case, facades.getValue(case.id)) }
        }
        val loader = URLClassLoader(arrayOf(outDir.toURI().toURL()), Thread.currentThread().contextClassLoader)
        return cases.associate { case -> case.id to Result(invoke(loader, facades.getValue(case.id))) }
    }

    private fun compileAndRunOne(case: DifferentialTestCase, facadeName: String): Result {
        val oneOffDir = File(workDir, "one-off-${facadeName}").apply { mkdirs() }
        val compiled = compileBatch(listOf(case), mapOf(case.id to facadeName), oneOffDir)
        if (!compiled.success) return Result(DifferentialResult.CompileFailure(compiled.diagnostics))
        val loader = URLClassLoader(arrayOf(oneOffDir.toURI().toURL()), Thread.currentThread().contextClassLoader)
        return Result(invoke(loader, facadeName))
    }

    private fun compileBatch(cases: List<DifferentialTestCase>, facades: Map<String, String>, destination: File = outDir): CompileOutcome {
        val files = cases.map { case ->
            val facadeName = facades.getValue(case.id)
            File(workDir, "$facadeName.kt").apply { writeText("@file:JvmName(\"$facadeName\")\n${case.source}") }
        }
        val diagnostics = StringBuilder()
        var hadError = false
        val collector = object : MessageCollector {
            override fun clear() {}
            override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
                if (severity.isError) {
                    hadError = true
                    diagnostics.appendLine("$severity: $message${location?.let { " @ ${it.path}:${it.line}" } ?: ""}")
                }
            }
            override fun hasErrors() = hadError
        }
        val args = K2JVMCompilerArguments().apply {
            freeArgs = files.map { it.absolutePath }
            this.destination = destination.absolutePath
            classpath = System.getProperty("java.class.path")
            noStdlib = true
            noReflect = true
        }
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, args)
        return CompileOutcome(exitCode.code == 0 && !hadError, diagnostics.toString())
    }

    private fun invoke(loader: URLClassLoader, facadeName: String): DifferentialResult {
        val future = runnerExecutor.submit<DifferentialResult> {
            val out = ByteArrayOutputStream()
            val savedOut = System.out
            System.setOut(PrintStream(out, true))
            try {
                val method = loader.loadClass(facadeName).getMethod("main")
                method.invoke(null)
                DifferentialResult.Success(out.toString())
            } catch (e: InvocationTargetException) {
                val cause = e.cause ?: e
                DifferentialResult.RuntimeFailure(cause::class.qualifiedName ?: cause::class.java.name, cause.message)
            } catch (e: Throwable) {
                DifferentialResult.RuntimeFailure(e::class.qualifiedName ?: e::class.java.name, e.message)
            } finally {
                System.setOut(savedOut)
            }
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            // The underlying daemon thread may keep running; we can't safely force-kill a
            // CPU-bound loop. It's abandoned rather than blocking the suite on it.
            DifferentialResult.TimedOut(timeoutMs)
        }
    }

    override fun close() {
        runnerExecutor.shutdownNow()
        workDir.deleteRecursively()
    }

    data class Result(val result: DifferentialResult)
    private data class CompileOutcome(val success: Boolean, val diagnostics: String)

    companion object {
        private fun facadeNameFor(id: String, index: Int): String =
            "Fixture${index}_" + id.replace(Regex("[^A-Za-z0-9]"), "_")
    }
}
