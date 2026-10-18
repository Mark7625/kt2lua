package kt2lua.transpiler

/** Type-only host API metadata. Binding bodies are never compiled into the plugin. */
class LuaBindings(private val entries: Map<String, String> = emptyMap()) {
    val symbols = entries.filterKeys { it.startsWith("symbol.") }.mapKeys { it.key.removePrefix("symbol.") }
    private val aliases = mutableMapOf<String, String>()
    val classNames = entries.keys.filter { it.startsWith("lua.") }.map { it.removePrefix("lua.") }

    fun forSource(source: String): LuaBindings {
        val scoped = LuaBindings(entries)
        val imports = Regex("(?m)^\\s*import\\s+([\\w.*]+)(?:\\s+as\\s+(\\w+))?").findAll(source).toList()
        val candidates = symbols.entries.groupBy { it.key.substringAfterLast('.') }
        for ((short, matches) in candidates) {
            val imported = matches.filter { entry -> imports.any { imp ->
                val path = imp.groupValues[1]
                path == entry.key || path == entry.key.substringBeforeLast('.') + ".*"
            } }.map { it.value }.distinct()
            val unique = imported.singleOrNull() ?: matches.map { it.value }.distinct().singleOrNull()
            if (unique != null) scoped.aliases[short] = unique
        }
        for (imp in imports) {
            symbols[imp.groupValues[1]]?.let { type ->
                scoped.aliases[imp.groupValues[2].ifEmpty { imp.groupValues[1].substringAfterLast('.') }] = type
            }
        }
        return scoped
    }

    fun type(name: String): String? = symbols[name] ?: aliases[name] ?: name.takeIf { "lua.$it" in entries }
    fun luaName(name: String): String? = type(name)?.let { entries["lua.$it"] }
    fun factory(name: String): String? = type(name)?.let { entries["factory.$it"] }
    fun property(type: String, name: String): String? = entries["property.${this.type(type) ?: type}#$name"]
    fun global(path: String): String? = entries["global.$path"]
    fun events(payload: String): Map<String, String> =
        entries.filterKeys { it.startsWith("event.${type(payload) ?: payload}#") }.mapKeys { it.key.substringAfter('#') }
    fun method(type: String, name: String): Pair<Boolean, String?>? =
        entries["method.${this.type(type) ?: type}#$name"]?.let {
            (it.substringBefore('|') == "colon") to it.substringAfter('|').ifEmpty { null }
        }

    fun register(types: TypeAnalyzer) {
        for (name in classNames + aliases.keys + symbols.keys) {
            val canonical = type(name) ?: continue
            val methods = entries.filterKeys { it.startsWith("method.$canonical#") }
                .mapKeys { it.key.substringAfter('#') }
            val properties = entries.filterKeys { it.startsWith("property.$canonical#") }
                .mapKeys { it.key.substringAfter('#') }
            val info = ClassInfo(emptyList(), methods.filterValues { it.startsWith("colon|") }.keys, false,
                methodReturnTypes = methods.mapValues { it.value.substringAfter('|') },
                propertyTypes = properties, native = true)
            types.registerExternalClass(name, info)
            types.registerExternalClass(canonical, info)
        }
    }
}
