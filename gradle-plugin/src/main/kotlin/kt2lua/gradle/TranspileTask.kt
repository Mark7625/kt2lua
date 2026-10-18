package kt2lua.gradle

import kt2lua.transpiler.EmitStyle
import kt2lua.transpiler.discoverProjectFiles
import kt2lua.transpiler.transpileProject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Transpiles every `.kt` file under [sourceDir] (recursively) into a matching `.lua` file
 * under [outputDir], preserving [sourceDir]'s subdirectory layout — a package's directory
 * structure carries straight through. Processes them all together via [transpileProject], so a
 * reference in one file to another file's top-level declaration gets a `require(...)` call
 * inserted automatically. */
abstract class TranspileTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Defaults (see [Kt2LuaPlugin]'s convention) to the project directory's own
     * `kt2lua-style.properties` — the auto-detect path, same convention IntelliJ's own
     * `checkstyle.xml` support uses — but a build script can point `kt2lua { styleFile.set(...)
     * }` at a different file instead. Either way, [EmitStyle.load] treats the file simply not
     * existing as "use kt2lua's own defaults", not an error — `@Optional` because of exactly
     * that: the conventional path usually *won't* exist. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val styleFile: RegularFileProperty

    @TaskAction
    fun run() {
        val srcDir = sourceDir.get().asFile
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        val projectFiles = discoverProjectFiles(srcDir)
        val style = EmitStyle.load(styleFile.orNull?.asFile)
        val result = transpileProject(projectFiles, style)
        for ((name, code) in result.files) {
            val outFile = outDir.resolve("$name.lua")
            outFile.parentFile?.mkdirs()
            outFile.writeText(code)
            logger.lifecycle("kt2lua: transpiled $name.kt -> $name.lua")
        }
    }
}
