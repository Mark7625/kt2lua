package kt2lua.transpiler

import java.io.File

/** Recursively discovers every `.kt` file under [root], one [ProjectFile] per file. Each
 * file's [ProjectFile.name] is its path relative to [root] (slash-separated, no `.kt`
 * extension) — e.g. `root/pkg/Thing.kt` becomes `"pkg/Thing"` — so a package's directory
 * layout carries straight through to [transpileProject]'s output file names and `require(...)`
 * paths. */
fun discoverProjectFiles(root: File): List<ProjectFile> =
    root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { file -> ProjectFile(file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".kt"), file.readText()) }
        .sortedBy { it.name }
        .toList()
