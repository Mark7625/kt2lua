package kt2lua.interpreter

/** Stands in for any unknown host API object: indexing or calling it lazily returns another
 * auto-stub, so a script can touch a large, unstubbed host API without errors. */
fun autoStub(): LuaTable {
    val table = LuaTable()
    val meta = LuaTable()
    meta.rawSet("__index", NativeFunction("autostub_index") { args ->
        val self = args[0] as LuaTable
        val key = args.getOrNull(1)
        val existing = key?.let { self.entries[it] }
        val value = existing ?: autoStub().also { if (key != null) self.entries[key] = it }
        listOf(value)
    })
    meta.rawSet("__call", NativeFunction("autostub_call") { listOf(autoStub()) })
    table.metatable = meta
    return table
}

fun installAutoStubs(env: Environment, globalNames: Collection<String>) {
    for (name in globalNames) env.define(name, autoStub())
}
