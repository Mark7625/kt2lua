package kt2lua.transpiler

import java.io.File

/** Changes output paths before dependency resolution, so require paths stay consistent. */
fun ProjectFile.withLuaLayout(rootPackage: String? = null, lowercaseFiles: Boolean = false): ProjectFile {
    val prefix = rootPackage?.replace('.', '/')?.trim('/')?.let { "$it/" }
    val relative = if (prefix != null) name.removePrefix(prefix) else name
    val directory = relative.substringBeforeLast('/', "")
    val basename = relative.substringAfterLast('/').let { if (lowercaseFiles) it.lowercase(java.util.Locale.ROOT) else it }
    return copy(name = if (directory.isEmpty()) basename else "$directory/$basename")
}

/** Package declarations determine Lua module paths even when Kotlin files are stored
 * outside matching folders. Files without a package keep the caller's fallback path. */
fun projectFileForSource(fallbackName: String, source: String): ProjectFile {
    val tokens = Lexer(source).tokenize()
    if (tokens.first().type != TokenType.PACKAGE) return ProjectFile(fallbackName, source)
    val segments = mutableListOf<String>()
    var index = 1
    while (tokens[index].type == TokenType.IDENTIFIER) {
        segments.add(tokens[index++].text!!)
        if (tokens[index].type != TokenType.DOT) break
        index++
    }
    require(segments.isNotEmpty()) { "Package name is missing in $fallbackName" }
    return ProjectFile((segments + fallbackName.substringAfterLast('/')).joinToString("/"), source)
}

/** Recursively discovers `.kt` files. Declared packages determine output module paths;
 * files without a package retain their relative source paths. */
fun discoverProjectFiles(root: File): List<ProjectFile> =
    root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { file -> projectFileForSource(file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".kt"), file.readText()) }
        .sortedBy { it.name }
        .toList()
