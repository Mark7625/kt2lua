package kt2lua.transpiler.compatibility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompatibilityRegistryTest {
    private val sampleManifestText = """
        {
          "library": "kotlin-stdlib",
          "kotlinVersion": "2.3.0",
          "package": "kotlin.collections",
          "declarations": [
            {"id": "kotlin.collections.Iterable.map((T) -> R)", "name": "map", "kind": "extension", "status": "supported"},
            {"id": "kotlin.collections.Iterable.runningFold(R, (R, T) -> R)", "name": "runningFold", "kind": "extension", "status": "unsupported"},
            {"id": "kotlin.collections.Iterable.filter((T) -> Boolean)", "name": "filter", "kind": "extension", "status": "partial", "limitations": ["no nulls"]}
          ]
        }
    """.trimIndent()

    @Test
    fun `loads declarations and looks them up by id`() {
        val manifest = ManifestLoader.parse(sampleManifestText)
        val registry = CompatibilityRegistry.from(listOf(manifest))
        val mapId = DeclarationId("kotlin.collections", "Iterable", "map", listOf("(T) -> R"))
        assertEquals(CompatibilityStatus.SUPPORTED, registry.status(mapId))
    }

    @Test
    fun `unknown declarations report unknown status rather than throwing`() {
        val registry = CompatibilityRegistry.from(listOf(ManifestLoader.parse(sampleManifestText)))
        val neverRegistered = DeclarationId("kotlin.collections", "Iterable", "groupingBy", listOf("(T) -> K"))
        assertEquals(CompatibilityStatus.UNKNOWN, registry.status(neverRegistered))
    }

    @Test
    fun `partial status carries its limitations through`() {
        val registry = CompatibilityRegistry.from(listOf(ManifestLoader.parse(sampleManifestText)))
        val filterId = DeclarationId("kotlin.collections", "Iterable", "filter", listOf("(T) -> Boolean"))
        val entry = registry.entry(filterId)
        assertEquals(CompatibilityStatus.PARTIAL, entry?.status)
        assertEquals(listOf("no nulls"), entry?.limitations)
    }

    @Test
    fun `byPackage filters to just that package`() {
        val registry = CompatibilityRegistry.from(listOf(ManifestLoader.parse(sampleManifestText)))
        assertEquals(3, registry.byPackage("kotlin.collections").size)
        assertTrue(registry.byPackage("kotlin.sequences").isEmpty())
    }

    @Test
    fun `duplicate declaration ids across manifests are rejected`() {
        val manifest = ManifestLoader.parse(sampleManifestText)
        assertFailsWith<IllegalArgumentException> { CompatibilityRegistry.from(listOf(manifest, manifest)) }
    }

    @Test
    fun `every bundled manifest loads without error and has no duplicate ids`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (pkg in listOf("kotlin", "kotlin.collections", "kotlin.sequences", "kotlin.math", "kotlin.text", "kotlin.ranges", "kotlin.lang")) {
            assertTrue(registry.byPackage(pkg).isNotEmpty(), "expected at least one declaration under package $pkg")
        }
    }

    @Test
    fun `every bundled declaration's id round-trips through parse and toString unchanged`() {
        val registry = CompatibilityRegistry.loadBundled()
        for (entry in registry.all()) {
            assertEquals(entry.id, DeclarationId.parse(entry.id.toString()), "id did not round-trip: ${entry.id}")
        }
    }
}
