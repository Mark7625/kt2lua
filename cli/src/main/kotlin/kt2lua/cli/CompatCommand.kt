package kt2lua.cli

import kt2lua.transpiler.Emitter
import kt2lua.transpiler.compatibility.CompatibilityRegistry
import kt2lua.transpiler.compatibility.CompatibilityReport
import kt2lua.transpiler.compatibility.CompatibilityStatus
import kt2lua.transpiler.compatibility.CompatibilityValidator
import kt2lua.transpiler.compatibility.DeclarationEntry
import java.net.URLEncoder
import kotlin.system.exitProcess

internal fun runCompatCommand(args: Array<String>) {
    val sub = args.getOrNull(1)
    val registry = CompatibilityRegistry.loadBundled()
    when (sub) {
        "list" -> compatList(registry, args.getOrNull(2))
        "status" -> compatStatus(registry, args.getOrNull(2) ?: compatUsageError("compat status requires a name or id fragment to search for"))
        "validate" -> compatValidate(registry)
        "issue" -> compatIssue(registry, args.getOrNull(2) ?: compatUsageError("compat issue requires a name or id fragment to search for"))
        "report" -> compatReportCommand(registry, args.getOrNull(2) ?: compatUsageError("compat report requires 'table', 'issue' or 'badge'"))
        else -> compatUsageError("Usage: kt2lua compat list [package] | kt2lua compat status <query> | kt2lua compat validate | kt2lua compat issue <query> | kt2lua compat report table|issue|badge")
    }
}

private fun compatReportCommand(registry: CompatibilityRegistry, format: String) {
    val stats = CompatibilityReport.packageStats(registry)
    when (format) {
        "table" -> print(CompatibilityReport.markdownTable(stats))
        "issue" -> print(CompatibilityReport.issueBody(registry, stats))
        "badge" -> print(CompatibilityReport.badgeMarkdown(stats))
        else -> compatUsageError("compat report format must be 'table', 'issue' or 'badge', got '$format'")
    }
}

private fun compatList(registry: CompatibilityRegistry, packageName: String?) {
    if (packageName == null) {
        val byPackage = registry.all().groupBy { it.id.packageName }
        println("Bundled compatibility packages:")
        for ((pkg, entries) in byPackage.toSortedMap()) {
            val counts = CompatibilityStatus.entries.associateWith { status -> entries.count { it.status == status } }
            println("  $pkg (${entries.size}): supported=${counts[CompatibilityStatus.SUPPORTED]} partial=${counts[CompatibilityStatus.PARTIAL]} unsupported=${counts[CompatibilityStatus.UNSUPPORTED]}")
        }
        println()
        println("Run 'kt2lua compat list <package>' to see individual declarations.")
        return
    }
    val entries = registry.byPackage(packageName)
    if (entries.isEmpty()) {
        System.err.println("kt2lua: no bundled declarations under package '$packageName'")
        exitProcess(1)
    }
    for (entry in entries.sortedBy { it.id.toString() }) {
        println("${statusTag(entry.status)} ${entry.id}")
    }
}

private fun compatStatus(registry: CompatibilityRegistry, query: String) {
    val matches = registry.all().filter { it.id.toString().contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
    if (matches.isEmpty()) {
        println("No bundled declaration matches '$query'.")
        return
    }
    for ((index, entry) in matches.sortedBy { it.id.toString() }.withIndex()) {
        if (index > 0) println()
        printEntry(entry)
    }
}

private val OPERATION_CLASS_NAME = Regex("""[A-Za-z]+Operation( / [A-Za-z]+Operation)?""")

private fun compatValidate(registry: CompatibilityRegistry) {
    val operations = Emitter().stdlibOperationRegistry()
    val registryRoutedIds = registry.all()
        .filter { it.implementation?.let { impl -> OPERATION_CLASS_NAME.matches(impl) } == true }
        .map { it.id }
        .toSet()
    val issues = CompatibilityValidator.validate(registry, operations, onlyIds = registryRoutedIds)
    if (issues.isEmpty()) {
        println("No consistency issues found across ${registryRoutedIds.size} registry-routed declaration(s).")
        return
    }
    println("${issues.size} consistency issue(s) found:")
    for (issue in issues.sortedBy { it.id.toString() }) {
        println("  ${issue.id}: ${issue.message}")
    }
    exitProcess(1)
}

private const val GITHUB_REPO = "Mark7625/kt2lua"

private fun compatIssue(registry: CompatibilityRegistry, query: String) {
    val matches = registry.all().filter { it.id.toString().contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
    val entry = when (matches.size) {
        0 -> {
            println("No bundled declaration matches '$query'.")
            exitProcess(1)
        }
        1 -> matches.single()
        else -> {
            println("'$query' matches ${matches.size} declarations — be more specific:")
            for (m in matches.sortedBy { it.id.toString() }) println("  ${m.id}")
            exitProcess(1)
        }
    }
    if (entry.status == CompatibilityStatus.SUPPORTED) {
        println("${entry.id} is already fully supported — nothing to file.")
        return
    }
    val title = "Support ${entry.id}"
    val body = buildString {
        appendLine("**Declaration:** `${entry.id}`")
        appendLine("**Package:** `${entry.id.packageName}`")
        appendLine("**Current status:** ${entry.status.name.lowercase()}")
        entry.documentation?.let { appendLine().appendLine(it) }
        if (entry.limitations.isNotEmpty()) {
            appendLine().appendLine("**Known limitations:**")
            for (limitation in entry.limitations) appendLine("- $limitation")
        }
        appendLine().append("_Generated by `kt2lua compat issue`._")
    }
    println(title)
    println()
    println(body)
    println()
    val url = "https://github.com/$GITHUB_REPO/issues/new" +
        "?title=${URLEncoder.encode(title, "UTF-8")}" +
        "&body=${URLEncoder.encode(body, "UTF-8")}" +
        "&labels=${URLEncoder.encode("compatibility", "UTF-8")}"
    println(url)
}

private fun printEntry(entry: DeclarationEntry) {
    println("${entry.id}  [${entry.status.name.lowercase()}]")
    entry.documentation?.let { println("  $it") }
    entry.implementation?.let { println("  implementation: $it") }
    for (limitation in entry.limitations) println("  - $limitation")
}

private fun statusTag(status: CompatibilityStatus): String = when (status) {
    CompatibilityStatus.SUPPORTED -> "[supported]  "
    CompatibilityStatus.PARTIAL -> "[partial]    "
    CompatibilityStatus.UNSUPPORTED -> "[unsupported]"
    CompatibilityStatus.UNKNOWN -> "[unknown]    "
}

private fun compatUsageError(message: String): Nothing {
    System.err.println("kt2lua: $message")
    exitProcess(1)
}
