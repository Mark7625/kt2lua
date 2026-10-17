package kt2lua.cli

import kt2lua.interpreter.DirectoryModuleLoader
import kt2lua.interpreter.Interpreter
import kt2lua.interpreter.LuaLexer
import kt2lua.interpreter.LuaParser
import kt2lua.interpreter.installAutoStubs
import kt2lua.interpreter.interpret
import kt2lua.transpiler.EmitStyle
import kt2lua.transpiler.LexException
import kt2lua.transpiler.ParseException
import kt2lua.transpiler.ProjectFile
import kt2lua.transpiler.transpile
import kt2lua.transpiler.transpileProject
import java.io.File
import kotlin.system.exitProcess

/** kt2lua's own release version — kept in lockstep by hand with the `version = "..."` every
 * build-logic/libs/runescape module declares; there's no single shared source for it yet (see
 * the root README's build-system section), so this is the one place the CLI's own `--version`
 * needs to remember to bump alongside those. */
private const val VERSION = "0.1.0"

/** Default output location for both single- and multi-file transpiles when `-o`/`--out` isn't
 * given — relative to the current directory, matching the Gradle plugin's own
 * `build/generated/kt2lua` convention (a build tool's output belongs under `build/`, not
 * scattered alongside hand-written sources). */
private const val DEFAULT_OUT_DIR = "build/dist"

private val KNOWN_FLAGS = setOf("-o", "--out", "--run", "--mock-globals", "--style", "-h", "--help", "-v", "--version")

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        exitProcess(1)
    }

    val inputPaths = mutableListOf<String>()
    var outputPath: String? = null
    var run = false
    var mockGlobals: List<String> = emptyList()
    var stylePath: String? = null
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("-") && arg !in KNOWN_FLAGS) {
            usageError("Unknown option: $arg")
        }
        when (arg) {
            "-o", "--out" -> {
                outputPath = args.getOrNull(i + 1) ?: usageError("$arg requires a path argument")
                i++
            }
            "--run" -> run = true
            "--mock-globals" -> {
                val value = args.getOrNull(i + 1) ?: usageError("--mock-globals requires a comma-separated name list")
                mockGlobals = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                i++
            }
            "--style" -> {
                stylePath = args.getOrNull(i + 1) ?: usageError("--style requires a path argument")
                i++
            }
            "-h", "--help" -> {
                printHelp()
                return
            }
            "-v", "--version" -> {
                println("kt2lua $VERSION")
                return
            }
            else -> inputPaths.add(arg)
        }
        i++
    }

    if (inputPaths.isEmpty()) usageError("No input file given")

    // Explicit `--style <file>` wins; otherwise auto-detect a `kt2lua-style.properties` in the
    // current directory, same as how a `checkstyle.xml` dropped into a project gets picked up
    // without pointing anything at it. Either way, a missing file (or one that's only partial)
    // falls back to kt2lua's own defaults — see EmitStyle.load's own doc comment.
    val styleFile = stylePath?.let { File(it) } ?: EmitStyle.autoDetect(File("."))
    val style = EmitStyle.load(styleFile)

    if (inputPaths.size == 1 && inputPaths[0].endsWith(".lua")) {
        val inputFile = File(inputPaths[0])
        if (!inputFile.exists()) {
            System.err.println("kt2lua: file not found: ${inputPaths[0]}")
            exitProcess(1)
        }
        runLuaFile(inputFile, mockGlobals)
        return
    }

    for (path in inputPaths) {
        if (!File(path).exists()) {
            System.err.println("kt2lua: file not found: $path")
            exitProcess(1)
        }
    }

    if (inputPaths.size == 1) {
        transpileSingleFile(inputPaths[0], outputPath, run, style)
    } else {
        if (run) usageError("--run isn't supported with multiple input files (no single entry point to run)")
        transpileProjectFiles(inputPaths, outputPath, style)
    }
}

/** Prints [message] prefixed `kt2lua: ` plus a hint toward `--help`, then exits — for an
 * argument-shape mistake (an unknown flag, a missing required value, no input at all), as
 * opposed to a file-not-found or compile error, neither of which `--help` would actually
 * explain. Typed `Nothing` so a call reads naturally as the tail of an Elvis (`?:
 * usageError(...)`) without the compiler needing a separate `return`/`exitProcess` afterward. */
private fun usageError(message: String): Nothing {
    System.err.println("kt2lua: $message")
    System.err.println("Try 'kt2lua --help' for more information.")
    exitProcess(1)
}

private fun transpileSingleFile(inputPath: String, outputPath: String?, run: Boolean, style: EmitStyle) {
    val source = File(inputPath).readText()
    val result = try {
        transpile(source, style)
    } catch (e: Exception) {
        System.err.println("kt2lua: ${diagnosticFor(inputPath, e)}")
        exitProcess(1)
    }

    val resolvedOut = File(outputPath ?: "$DEFAULT_OUT_DIR/${File(inputPath).nameWithoutExtension}.lua")
    resolvedOut.parentFile?.mkdirs()
    resolvedOut.writeText(result.code)
    println("Wrote ${resolvedOut.path}")

    if (run) {
        try {
            interpret(result.code)
        } catch (e: Exception) {
            System.err.println("kt2lua: ${e.message}")
            exitProcess(1)
        }
    }
}

/**
 * Transpiles several `.kt` files *together*, so [kt2lua.transpiler.transpileProject] can see
 * cross-file references and insert the `require(...)` calls they need automatically — the thing
 * a single-file `kt2lua file.kt -o file.lua` invocation structurally can't do, since it never
 * sees any other file's declarations. `-o` here names an output *directory*, not a file: each
 * input's own base name (no `.kt`) becomes `<outDir>/<name>.lua`, and that same base name is
 * also what sibling files `require(...)` it as. Defaults to [DEFAULT_OUT_DIR] when `-o` isn't
 * given, same as the single-file case.
 */
private fun transpileProjectFiles(inputPaths: List<String>, outputPath: String?, style: EmitStyle) {
    val projectFiles = inputPaths.map { path ->
        ProjectFile(File(path).nameWithoutExtension, File(path).readText())
    }
    val result = try {
        transpileProject(projectFiles, style)
    } catch (e: Exception) {
        System.err.println("kt2lua: ${e.message ?: e.toString()}")
        exitProcess(1)
    }
    val outDir = File(outputPath ?: DEFAULT_OUT_DIR)
    outDir.mkdirs()
    for ((name, code) in result.files) {
        val outFile = outDir.resolve("$name.lua")
        outFile.writeText(code)
        println("Wrote ${outFile.path}")
    }
}

/**
 * Runs a hand-written/compiled `.lua` file directly, with `require(...)` resolved relative to
 * its directory. [mockGlobals] are pre-defined as [kt2lua.interpreter.autoStub] values before
 * running, for smoke-testing scripts against a host API (e.g. the RS3 client's globals) you
 * don't want to hand-stub piece by piece.
 */
private fun runLuaFile(file: File, mockGlobals: List<String>) {
    try {
        val interp = Interpreter(moduleLoader = DirectoryModuleLoader(file.parentFile ?: File(".")))
        installAutoStubs(interp.globals, mockGlobals)
        val chunk = LuaParser(LuaLexer(file.readText()).tokenize()).parseChunk()
        interp.run(chunk)
    } catch (e: Exception) {
        System.err.println("kt2lua: ${e.message}")
        exitProcess(1)
    }
}

/** Formats `path:line:col: message` for positioned diagnostics, falling back to the bare
 * message for anything else (e.g. an I/O error). */
private fun diagnosticFor(path: String, e: Exception): String = when (e) {
    is LexException -> "$path:${e.line}:${e.col}: ${e.rawMessage}"
    is ParseException -> "$path:${e.line}:${e.col}: ${e.rawMessage}"
    else -> e.message ?: e.toString()
}

private fun printHelp() {
    println(
        """
        kt2lua $VERSION - a Kotlin to Lua transpiler, with its own Lua interpreter

        Usage:
          kt2lua <input.kt> [options]              Transpile one file
          kt2lua <input.kt...> [options]           Transpile several files together,
                                                    auto-requiring cross-file references
          kt2lua <input.lua> [--mock-globals ...]  Run a Lua file directly
          kt2lua -h | --help                       Show this help message
          kt2lua -v | --version                    Show the version number

        By default, output goes to build/dist/ in the current directory (<name>.lua per
        input, same as the Gradle plugin's own build/generated/kt2lua convention) - pass
        -o/--out to write somewhere else instead.

        Transpiling several files together lets the transpiler see every file's top-level
        declarations at once: a reference in one file to a name declared at the top level of
        another (an `object`, a non-private top-level `fun`/`val`/`var`, a `class`) gets a
        `require(...)` call inserted automatically, instead of needing one written by hand.
        Anything that isn't any input file's own top-level declaration is still treated as an
        ambient global, same as single-file mode.

        Running a .lua file resolves require(...) to sibling .lua files relative to its
        directory. --mock-globals pre-defines the given global names as auto-stub values
        (any field access or call on them succeeds and yields another auto-stub), useful for
        loading a plugin bundle that expects a large host API you haven't hand-stubbed.

        Output formatting (indentation, blank lines, string quoting) follows kt2lua's own
        style by default. Drop a kt2lua-style.properties file in the current directory to
        override any of it (indent_size, indent_style, quote_style,
        blank_line_before_declarations) - auto-detected with no flag needed, same as how an
        IDE picks up a checkstyle.xml. A missing file, or one that only sets some of those
        keys, falls back to kt2lua's own default for whatever it doesn't set.

        Options:
          -o, --out <path>        Output file (single input) or directory (multiple inputs);
                                   defaults to build/dist
          --run                    Execute the generated Lua with kt2lua's own built-in
                                   interpreter (single input only)
          --mock-globals <names>   Comma-separated global names to auto-stub (.lua mode only)
          --style <file>           Use this style file instead of auto-detecting
                                   kt2lua-style.properties
          -h, --help               Show this help message
          -v, --version            Show the version number

        Examples:
          kt2lua hello.kt --run
          kt2lua Main.kt ModuleWindow.kt -o build/dist
          kt2lua build/dist/main.lua --mock-globals Plugin,Event,GameState
        """.trimIndent()
    )
}
