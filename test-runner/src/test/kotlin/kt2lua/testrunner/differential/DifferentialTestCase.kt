package kt2lua.testrunner.differential

import java.io.File

/**
 * A single differential fixture: a Kotlin source file under
 * `src/test/resources/differential-fixtures/<category>/<name>.kt`. [id] is the path relative to
 * the fixtures root with the extension stripped (e.g. "collections/list/map-basic"); [category] is
 * its top-level subdirectory.
 */
data class DifferentialTestCase(val id: String, val category: String, val source: String)

object DifferentialFixtures {
    private val root = File("src/test/resources/differential-fixtures")

    fun discover(categoryFilter: String? = null): List<DifferentialTestCase> {
        check(root.isDirectory) { "$root not found (expected working directory to be the test-runner module root)" }
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".kt")
                DifferentialTestCase(id = relative, category = relative.substringBefore('/'), source = file.readText())
            }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .sortedBy { it.id }
            .toList()
    }
}
