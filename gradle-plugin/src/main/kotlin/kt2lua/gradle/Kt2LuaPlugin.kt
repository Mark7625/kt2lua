package kt2lua.gradle

import kt2lua.transpiler.EmitStyle
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the `kt2lua` extension and registers the `transpileKotlinToLua` task, which runs the
 * kt2lua transpiler over `kt2lua.sourceDir` and writes `.lua` files into `kt2lua.outputDir`.
 */
class Kt2LuaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kt2lua", Kt2LuaExtension::class.java)
        extension.sourceDir.convention(project.layout.projectDirectory.dir("src/main/kotlin-scripts"))
        extension.outputDir.convention(project.layout.buildDirectory.dir("generated/kt2lua"))
        // Defaults to the auto-detect path (the project directory's own kt2lua-style.properties)
        // — it usually won't exist, and that's fine, [EmitStyle.load] treats a missing file as
        // "use kt2lua's own defaults". A build script can still override with its own path via
        // `kt2lua { styleFile.set(file("...")) }`.
        extension.styleFile.convention(project.layout.projectDirectory.file(EmitStyle.FILE_NAME))

        project.tasks.register("transpileKotlinToLua", TranspileTask::class.java) { task ->
            task.sourceDir.set(extension.sourceDir)
            task.outputDir.set(extension.outputDir)
            task.styleFile.set(extension.styleFile)
            task.group = "kt2lua"
            task.description = "Transpiles Kotlin scripts to Lua using kt2lua"
        }
    }
}
