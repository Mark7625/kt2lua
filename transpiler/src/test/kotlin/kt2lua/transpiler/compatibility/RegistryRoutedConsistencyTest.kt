package kt2lua.transpiler.compatibility

import kt2lua.transpiler.Emitter
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistryRoutedConsistencyTest {
    @Test
    fun `every registry-routed declaration is consistent, except the one documented CountOperation gap`() {
        val registry = CompatibilityRegistry.loadBundled()
        val operations = Emitter().stdlibOperationRegistry()
        val operationClassName = Regex("""[A-Za-z]+Operation( / [A-Za-z]+Operation)?""")
        val registryRoutedIds = registry.all()
            .filter { it.implementation?.let { impl -> operationClassName.matches(impl) } == true }
            .map { it.id }
            .toSet()
        val issues = CompatibilityValidator.validate(registry, operations, onlyIds = registryRoutedIds)
        val knownAcceptedGap = DeclarationId("kotlin.collections", "Collection", "count")
        assertEquals(setOf(knownAcceptedGap), issues.map { it.id }.toSet(), "unexpected registry/manifest drift:\n${issues.joinToString("\n") { "${it.id}: ${it.message}" }}")
    }
}
