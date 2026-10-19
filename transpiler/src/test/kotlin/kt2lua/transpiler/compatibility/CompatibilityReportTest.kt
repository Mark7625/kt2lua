package kt2lua.transpiler.compatibility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityReportTest {
    private val fooId = DeclarationId("kotlin.foo", null, "a")
    private val barId = DeclarationId("kotlin.foo", null, "b")
    private val bazId = DeclarationId("kotlin.foo", null, "c")
    private val pseudoId = DeclarationId("kotlin.lang", null, "x")

    private fun manifestWith(pkg: String, vararg entries: DeclarationEntry) =
        CompatibilityManifest("kotlin-stdlib", "2.3.0", pkg, null, entries.toList())

    @Test
    fun `packageStats counts each status and computes percent complete`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.foo",
            DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
            DeclarationEntry(barId, "b", "function", CompatibilityStatus.PARTIAL),
            DeclarationEntry(bazId, "c", "function", CompatibilityStatus.UNSUPPORTED),
        )))
        val stats = CompatibilityReport.packageStats(registry).single()
        assertEquals(1, stats.supported)
        assertEquals(1, stats.partial)
        assertEquals(1, stats.unsupported)
        assertEquals(3, stats.total)
        assertEquals(2, stats.added)
        assertEquals(33, stats.percentComplete)
        assertEquals(67, stats.percentRemaining)
    }

    @Test
    fun `the pseudo package kotlin lang is excluded from the report entirely`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.lang",
            DeclarationEntry(pseudoId, "x", "language-feature", CompatibilityStatus.SUPPORTED),
        )))
        assertTrue(CompatibilityReport.packageStats(registry).isEmpty())
    }

    @Test
    fun `docsUrl matches the real kotlinlang org pattern`() {
        assertEquals("https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/", CompatibilityReport.docsUrl("kotlin.collections"))
    }

    @Test
    fun `markdownTable includes a link, counts and a closing Total row for every package`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.foo",
            DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
            DeclarationEntry(barId, "b", "function", CompatibilityStatus.UNSUPPORTED),
        )))
        val table = CompatibilityReport.markdownTable(CompatibilityReport.packageStats(registry))
        assertTrue(table.contains("[kotlin.foo](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.foo/)"))
        assertTrue(table.contains("1/2"))
        assertTrue(table.contains("**Total**"))
    }

    @Test
    fun `markdownTable has a separate Partial column that is excluded from percent complete`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.foo",
            DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
            DeclarationEntry(barId, "b", "function", CompatibilityStatus.PARTIAL),
            DeclarationEntry(bazId, "c", "function", CompatibilityStatus.UNSUPPORTED),
        )))
        val stats = CompatibilityReport.packageStats(registry)
        val table = CompatibilityReport.markdownTable(stats)
        assertTrue(table.contains("| Package | Done | Partial | Left | % Complete |"))
        assertTrue(table.contains("| [kotlin.foo](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.foo/) | 1/3 | 1 | 1 | 33% |"))
    }

    @Test
    fun `badgeMarkdown embeds the overall percent and a traffic-light color`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.foo",
            DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
            DeclarationEntry(barId, "b", "function", CompatibilityStatus.UNSUPPORTED),
        )))
        val stats = CompatibilityReport.packageStats(registry)
        val badge = CompatibilityReport.badgeMarkdown(stats)
        assertTrue(badge.contains("stdlib%20coverage-50%25-yellow"))
        assertEquals("brightgreen", CompatibilityReport.badgeColor(80))
        assertEquals("yellow", CompatibilityReport.badgeColor(50))
        assertEquals("orange", CompatibilityReport.badgeColor(25))
        assertEquals("red", CompatibilityReport.badgeColor(24))
    }

    @Test
    fun `issueBody lists every non-supported declaration under its package, skips fully-supported packages`() {
        val registry = CompatibilityRegistry.from(listOf(
            manifestWith(
                "kotlin.foo",
                DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
                DeclarationEntry(barId, "b", "function", CompatibilityStatus.UNSUPPORTED, limitations = listOf("no codegen")),
            ),
        ))
        val stats = CompatibilityReport.packageStats(registry)
        val body = CompatibilityReport.issueBody(registry, stats)
        assertTrue(body.contains("<summary>kotlin.foo"))
        assertTrue(body.contains("`kotlin.foo.b()`"))
        assertTrue(body.contains("no codegen"))
        assertFalse(body.contains("`kotlin.foo.a()`"), "a fully-supported declaration shouldn't be listed in the spoiler")
    }

    @Test
    fun `unsupported declarations with no notes are grouped onto one line`() {
        val registry = CompatibilityRegistry.from(listOf(
            manifestWith(
                "kotlin.foo",
                DeclarationEntry(fooId, "a", "function", CompatibilityStatus.UNSUPPORTED),
                DeclarationEntry(barId, "b", "function", CompatibilityStatus.UNSUPPORTED),
            ),
        ))
        val body = CompatibilityReport.issueBody(registry, CompatibilityReport.packageStats(registry))
        assertTrue(body.contains("Not started (2): `a`, `b`"))
        assertFalse(body.contains("- [ ]"))
    }

    @Test
    fun `a package where everything is fully supported gets no spoiler section at all`() {
        val registry = CompatibilityRegistry.from(listOf(manifestWith(
            "kotlin.foo",
            DeclarationEntry(fooId, "a", "function", CompatibilityStatus.SUPPORTED),
        )))
        val stats = CompatibilityReport.packageStats(registry)
        assertFalse(CompatibilityReport.issueBody(registry, stats).contains("<details>"))
    }
}
