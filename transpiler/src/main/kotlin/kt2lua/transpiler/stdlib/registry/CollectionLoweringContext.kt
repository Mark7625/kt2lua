package kt2lua.transpiler.stdlib.registry

import kt2lua.transpiler.Expression
import kt2lua.transpiler.Statement
import kt2lua.transpiler.TypeName

interface CollectionLoweringContext {
    fun line(text: String)
    fun pushIndent()
    fun popIndent()
    fun emitExpression(expr: Expression): String
    fun emitStatement(stmt: Statement)

    fun pushScope()
    fun popScope()
    fun declareVar(name: String, type: TypeName = TypeName.ANY)
    fun declareInstance(name: String, className: String)

    fun receiverInstanceClass(expr: Expression): String?

    fun isNativeClass(className: String): Boolean

    fun hoistIfNeeded(expr: Expression): String

    fun isRepeatableExpr(expr: Expression): Boolean

    fun iterationArrayOf(expr: Expression): String

    fun emitLambdaResult(statement: Statement, sink: (String) -> String)

    fun nextTemp(prefix: String): String

    fun isModuleCall(expr: Expression): Boolean

    fun listElementClassOf(name: String): String?
}
