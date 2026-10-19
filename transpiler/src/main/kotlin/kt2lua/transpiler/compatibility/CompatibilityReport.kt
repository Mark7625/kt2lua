package kt2lua.transpiler.compatibility

data class PackageStats(
    val packageName: String,
    val supported: Int,
    val partial: Int,
    val unsupported: Int,
) {
    val total: Int get() = supported + partial + unsupported

    val added: Int get() = supported + partial

    val percentComplete: Int get() = if (total == 0) 100 else (supported * 100) / total
    val percentRemaining: Int get() = 100 - percentComplete
}

object CompatibilityReport {
    private const val PSEUDO_PACKAGE = "kotlin.lang"

    fun docsUrl(packageName: String): String = "https://kotlinlang.org/api/core/kotlin-stdlib/$packageName/"

    fun packageStats(registry: CompatibilityRegistry): List<PackageStats> =
        registry.all()
            .filter { it.id.packageName != PSEUDO_PACKAGE }
            .groupBy { it.id.packageName }
            .map { (pkg, entries) ->
                PackageStats(
                    packageName = pkg,
                    supported = entries.count { it.status == CompatibilityStatus.SUPPORTED },
                    partial = entries.count { it.status == CompatibilityStatus.PARTIAL },
                    unsupported = entries.count { it.status != CompatibilityStatus.SUPPORTED && it.status != CompatibilityStatus.PARTIAL },
                )
            }
            .sortedBy { it.packageName }

    fun overallPercentComplete(stats: List<PackageStats>): Int {
        val totalSupported = stats.sumOf { it.supported }
        val totalDeclarations = stats.sumOf { it.total }
        return if (totalDeclarations == 0) 100 else (totalSupported * 100) / totalDeclarations
    }

    fun badgeColor(percent: Int): String = when {
        percent >= 80 -> "brightgreen"
        percent >= 50 -> "yellow"
        percent >= 25 -> "orange"
        else -> "red"
    }

    fun badgeMarkdown(stats: List<PackageStats>): String {
        val percent = overallPercentComplete(stats)
        return "<img src=\"https://img.shields.io/badge/stdlib%20coverage-$percent%25-${badgeColor(percent)}\" alt=\"Kotlin stdlib coverage\">"
    }

    fun markdownTable(stats: List<PackageStats>): String = buildString {
        appendLine("| Package | Done | Partial | Left | % Complete |")
        appendLine("|---|---|---|---|---|")
        for (s in stats) {
            appendLine("| [${s.packageName}](${docsUrl(s.packageName)}) | ${s.supported}/${s.total} | ${s.partial} | ${s.unsupported} | ${s.percentComplete}% |")
        }
        val totalSupported = stats.sumOf { it.supported }
        val totalDeclarations = stats.sumOf { it.total }
        val totalPartial = stats.sumOf { it.partial }
        val totalUnsupported = stats.sumOf { it.unsupported }
        appendLine("| **Total** | **$totalSupported/$totalDeclarations** | **$totalPartial** | **$totalUnsupported** | **${overallPercentComplete(stats)}%** |")
        appendLine()
        appendLine("_Done = fully implemented; Partial = implemented with a real caveat, not counted toward % Complete; Left = not started at all. See the tracking issue for exactly what each partial entry is missing._")
    }

    fun issueBody(registry: CompatibilityRegistry, stats: List<PackageStats>): String = buildString {
        appendLine("Tracking issue for kt2lua's Kotlin stdlib compatibility, generated from the bundled compatibility manifests. Auto-updated on every push to main — don't hand-edit this body, it'll be overwritten.")
        appendLine()
        append(markdownTable(stats))
        appendLine()
        for (s in stats) {
            val remaining = registry.byPackage(s.packageName)
                .filter { it.status != CompatibilityStatus.SUPPORTED }
                .sortedBy { it.id.toString() }
            if (remaining.isEmpty()) continue
            appendLine("<details>")
            appendLine("<summary>${s.packageName} — ${s.percentComplete}% has some implementation (${s.partial} partial, ${s.unsupported} not started)</summary>")
            appendLine()
            val (bare, detailed) = remaining.partition { it.status == CompatibilityStatus.UNSUPPORTED && it.limitations.isEmpty() }
            for (entry in detailed) {
                val tag = if (entry.status == CompatibilityStatus.PARTIAL) "partial" else "unsupported"
                appendLine("- [ ] `${entry.id}` _(${tag})_" + (entry.limitations.firstOrNull()?.let { " — $it" } ?: ""))
            }
            if (bare.isNotEmpty()) {
                // Entries with nothing to say beyond "not started" share one line, which keeps the body
                // under GitHub's 65,536-character issue limit now that the manifests list every name.
                val names = bare.map { it.name }.distinct().sorted()
                if (detailed.isNotEmpty()) appendLine()
                appendLine("Not started (${names.size}): " + names.joinToString(", ") { "`$it`" })
            }
            appendLine()
            appendLine("</details>")
            appendLine()
        }
    }
}
