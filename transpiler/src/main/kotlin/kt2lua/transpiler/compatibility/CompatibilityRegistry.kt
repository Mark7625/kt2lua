package kt2lua.transpiler.compatibility

class CompatibilityRegistry private constructor(private val byId: Map<DeclarationId, DeclarationEntry>) {
    fun status(id: DeclarationId): CompatibilityStatus = byId[id]?.status ?: CompatibilityStatus.UNKNOWN
    fun entry(id: DeclarationId): DeclarationEntry? = byId[id]
    fun all(): Collection<DeclarationEntry> = byId.values
    fun byPackage(packageName: String): List<DeclarationEntry> = byId.values.filter { it.id.packageName == packageName }

    companion object {
        private val BUNDLED_RESOURCE_PATHS = listOf(
            "/compatibility/kotlin-stdlib/kotlin.json",
            "/compatibility/kotlin-stdlib/kotlin.collections.json",
            "/compatibility/kotlin-stdlib/kotlin.sequences.json",
            "/compatibility/kotlin-stdlib/kotlin.math.json",
            "/compatibility/kotlin-stdlib/kotlin.text.json",
            "/compatibility/kotlin-stdlib/kotlin.ranges.json",
            "/compatibility/kotlin-stdlib/kotlin.lang.json",
            "/compatibility/kotlin-stdlib/kotlin.io.json",
            "/compatibility/kotlin-stdlib/kotlin.io.path.json",
            "/compatibility/kotlin-stdlib/kotlin.time.json",
            "/compatibility/kotlin-stdlib/kotlin.streams.json",
            "/compatibility/kotlin-stdlib/kotlin.system.json",
            "/compatibility/kotlin-stdlib/kotlin.random.json",
            "/compatibility/kotlin-stdlib/kotlin.enums.json",
        )

        fun from(manifests: List<CompatibilityManifest>): CompatibilityRegistry {
            val byId = mutableMapOf<DeclarationId, DeclarationEntry>()
            for (manifest in manifests) {
                for (decl in manifest.declarations) {
                    require(decl.id !in byId) { "Duplicate declaration id across manifests: ${decl.id}" }
                    byId[decl.id] = decl
                }
            }
            return CompatibilityRegistry(byId)
        }

        fun loadBundled(): CompatibilityRegistry = from(BUNDLED_RESOURCE_PATHS.map { ManifestLoader.loadResource(it) })
    }
}
