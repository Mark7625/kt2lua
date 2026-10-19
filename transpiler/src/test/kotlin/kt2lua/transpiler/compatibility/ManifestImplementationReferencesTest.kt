package kt2lua.transpiler.compatibility

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ManifestImplementationReferencesTest {
    @Test
    fun `every 'Emitter dot methodName' implementation reference still exists in Emitter kt`() {
        val emitterSource = findEmitterKt().readText()
        val definedNames = Regex("""fun\s+(\w+)\s*\(""").findAll(emitterSource).map { it.groupValues[1] }.toSet()
        val registry = CompatibilityRegistry.loadBundled()
        val staleReferences = mutableListOf<String>()
        for (entry in registry.all()) {
            val implementation = entry.implementation ?: continue
            for (match in Regex("""Emitter\.(\w+)""").findAll(implementation)) {
                val methodName = match.groupValues[1]
                if (methodName != "kt" && methodName !in definedNames) {
                    staleReferences += "${entry.id} claims Emitter.$methodName, which no longer exists"
                }
            }
        }
        assertTrue(staleReferences.isEmpty(), "Stale 'implementation' references found:\n${staleReferences.joinToString("\n")}")
    }

    private fun findEmitterKt(): File {
        var dir = File(".").absoluteFile
        while (true) {
            val candidate = File(dir, "transpiler/src/main/kotlin/kt2lua/transpiler/Emitter.kt")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: error("Could not locate Emitter.kt by walking up from the working directory")
        }
    }
}
