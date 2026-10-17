package kt2lua.interpreter

import java.io.File

/** Resolves `require("modules/xp")` to `<baseDir>/modules/xp.lua` — slash-separated relative
 * paths, not dotted package names. */
class DirectoryModuleLoader(private val baseDir: File) : ModuleLoader {
    override fun load(moduleName: String): String? {
        val candidates = listOf(
            File(baseDir, "$moduleName.lua"),
            File(baseDir, "${moduleName.replace('.', '/')}.lua"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
    }
}
