package kt2lua.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import javax.inject.Inject

/** Configuration block exposed to build scripts as `kt2lua { ... }`. [styleFile] defaults (see
 * [Kt2LuaPlugin]) to the project directory's own `kt2lua-style.properties`, auto-detected with
 * no configuration needed; leave it alone unless you want a style file somewhere else. Either
 * way, [TranspileTask] falls back to kt2lua's own default style for whatever the file doesn't
 * exist or doesn't set — see [kt2lua.transpiler.EmitStyle]. */
abstract class Kt2LuaExtension @Inject constructor() {
    abstract val sourceDir: DirectoryProperty
    abstract val outputDir: DirectoryProperty
    abstract val styleFile: RegularFileProperty
}
