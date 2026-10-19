package kt2lua.transpiler.compatibility

enum class CompatibilityStatus {
    SUPPORTED,

    PARTIAL,

    UNSUPPORTED,

    UNKNOWN,
}

data class DeclarationEntry(
    val id: DeclarationId,
    val name: String,
    val kind: String,
    val status: CompatibilityStatus,
    val documentation: String? = null,
    val limitations: List<String> = emptyList(),
    val implementation: String? = null,
    val tests: List<String> = emptyList(),
)

data class CompatibilityManifest(
    val library: String,
    val libraryVersion: String,
    val packageName: String,
    val documentation: String?,
    val declarations: List<DeclarationEntry>,
)
