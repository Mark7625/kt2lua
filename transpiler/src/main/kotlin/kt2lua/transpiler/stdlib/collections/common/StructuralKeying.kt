package kt2lua.transpiler.stdlib.collections.common

import kt2lua.transpiler.Expression
import kt2lua.transpiler.stdlib.registry.CollectionLoweringContext

fun CollectionLoweringContext.canonicalKeyStr(keyExpr: Expression, keyStr: String): String {
    val className = receiverInstanceClass(keyExpr) ?: return keyStr
    return if (!isNativeClass(className)) "tostring($keyStr)" else keyStr
}

fun CollectionLoweringContext.structuralEq(sampleExpr: Expression, aStr: String, bStr: String): String {
    val className = receiverInstanceClass(sampleExpr) ?: return "($aStr == $bStr)"
    return if (!isNativeClass(className)) "(tostring($aStr) == tostring($bStr))" else "($aStr == $bStr)"
}
