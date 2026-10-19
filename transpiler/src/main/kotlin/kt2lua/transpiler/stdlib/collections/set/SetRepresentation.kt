package kt2lua.transpiler.stdlib.collections.set

import kt2lua.transpiler.Expression
import kt2lua.transpiler.stdlib.collections.common.canonicalKeyStr
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.emitSetAdd(setVar: String, elemStr: String, elemExpr: Expression? = null) {
    if (elemExpr == null || canonicalKeyStr(elemExpr, elemStr) == elemStr) {
        line("if not $setVar.has[$elemStr] then table.insert($setVar.list, $elemStr); $setVar.has[$elemStr] = true end")
        return
    }
    val elemVar = nextTemp("__elem")
    line("local $elemVar = $elemStr")
    val keyStr = canonicalKeyStr(elemExpr, elemVar)
    line("if not $setVar.has[$keyStr] then table.insert($setVar.list, $elemVar); $setVar.has[$keyStr] = true end")
}

fun CollectionLoweringContext.emitSetRemove(setVar: String, elemStr: String, elemExpr: Expression? = null) {
    if (elemExpr == null || canonicalKeyStr(elemExpr, elemStr) == elemStr) {
        line("if $setVar.has[$elemStr] then")
        pushIndent()
        line("$setVar.has[$elemStr] = nil")
        line("for __i, __e in ipairs($setVar.list) do if __e == $elemStr then table.remove($setVar.list, __i); break end end")
        popIndent()
        line("end")
        return
    }
    val elemVar = nextTemp("__elem")
    line("local $elemVar = $elemStr")
    val keyStr = canonicalKeyStr(elemExpr, elemVar)
    line("if $setVar.has[$keyStr] then")
    pushIndent()
    line("$setVar.has[$keyStr] = nil")
    line("for __i, __e in ipairs($setVar.list) do if tostring(__e) == $keyStr then table.remove($setVar.list, __i); break end end")
    popIndent()
    line("end")
}
