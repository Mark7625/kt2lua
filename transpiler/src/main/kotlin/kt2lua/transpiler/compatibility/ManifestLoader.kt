package kt2lua.transpiler.compatibility

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

private data class RawManifest(
    val library: String? = null,
    val kotlinVersion: String? = null,
    val libraryVersion: String? = null,
    val `package`: String? = null,
    val documentation: String? = null,
    val declarations: List<RawDeclaration> = emptyList(),
)

private data class RawDeclaration(
    val id: String? = null,
    val name: String? = null,
    val kind: String = "function",
    val status: String? = null,
    val documentation: String? = null,
    val limitations: List<String> = emptyList(),
    val implementation: String? = null,
    val tests: List<String> = emptyList(),
)

object ManifestLoader {
    private val gson = Gson()

    fun loadResource(resourcePath: String): CompatibilityManifest {
        val stream = ManifestLoader::class.java.getResourceAsStream(resourcePath)
            ?: error("Compatibility manifest not found on classpath: $resourcePath")
        return parse(stream.bufferedReader().readText())
    }

    fun parse(text: String): CompatibilityManifest {
        val raw = try {
            gson.fromJson(text, RawManifest::class.java)
        } catch (e: JsonSyntaxException) {
            throw JsonSyntaxException("Malformed compatibility manifest", e)
        } ?: throw JsonSyntaxException("Manifest must be a JSON object")
        return CompatibilityManifest(
            library = raw.library ?: error("Missing required field 'library'"),
            libraryVersion = raw.kotlinVersion ?: raw.libraryVersion ?: "unknown",
            packageName = raw.`package` ?: error("Missing required field 'package'"),
            documentation = raw.documentation,
            declarations = raw.declarations.map { it.toEntry() },
        )
    }

    private fun RawDeclaration.toEntry(): DeclarationEntry {
        val declId = id ?: error("Declaration is missing required field 'id'")
        return DeclarationEntry(
            id = DeclarationId.parse(declId),
            name = name ?: error("Declaration '$declId' is missing required field 'name'"),
            kind = kind,
            status = when (status) {
                "supported" -> CompatibilityStatus.SUPPORTED
                "partial" -> CompatibilityStatus.PARTIAL
                "unsupported" -> CompatibilityStatus.UNSUPPORTED
                else -> CompatibilityStatus.UNKNOWN
            },
            documentation = documentation,
            limitations = limitations,
            implementation = implementation,
            tests = tests,
        )
    }
}
