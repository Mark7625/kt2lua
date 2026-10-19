package kt2lua.transpiler.stdlib.collections.map

import kt2lua.transpiler.Expression
import kt2lua.transpiler.stdlib.collections.common.canonicalKeyStr
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitMapPut(mapVar: String, keyStr: String, valueStr: String, keyExpr: Expression? = null) {
    if (keyExpr == null || canonicalKeyStr(keyExpr, keyStr) == keyStr) {
        line("if $mapVar.map[$keyStr] == nil then table.insert($mapVar.keys, $keyStr) end")
        line("$mapVar.map[$keyStr] = $valueStr")
        return
    }
    val keyVar = nextTemp("__key")
    line("local $keyVar = $keyStr")
    val canonical = canonicalKeyStr(keyExpr, keyVar)
    line("if $mapVar.map[$canonical] == nil then table.insert($mapVar.keys, $keyVar) end")
    line("$mapVar.map[$canonical] = $valueStr")
}

fun CollectionLoweringContext.emitMapRemove(mapVar: String, keyStr: String, keyExpr: Expression? = null) {
    if (keyExpr == null || canonicalKeyStr(keyExpr, keyStr) == keyStr) {
        line("if $mapVar.map[$keyStr] ~= nil then")
        pushIndent()
        line("$mapVar.map[$keyStr] = nil")
        line("for __i, __k in ipairs($mapVar.keys) do if __k == $keyStr then table.remove($mapVar.keys, __i); break end end")
        popIndent()
        line("end")
        return
    }
    val keyVar = nextTemp("__key")
    line("local $keyVar = $keyStr")
    val canonical = canonicalKeyStr(keyExpr, keyVar)
    line("if $mapVar.map[$canonical] ~= nil then")
    pushIndent()
    line("$mapVar.map[$canonical] = nil")
    line("for __i, __k in ipairs($mapVar.keys) do if tostring(__k) == $canonical then table.remove($mapVar.keys, __i); break end end")
    popIndent()
    line("end")
}
