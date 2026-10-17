package kt2lua.interpreter

data class Chunk(val body: List<Stmt>)

sealed class Stmt
data class LocalDecl(val names: List<String>, val exprs: List<Expr>) : Stmt()
data class LocalFunctionDecl(val name: String, val params: List<String>, val isVararg: Boolean, val body: List<Stmt>) : Stmt()

sealed class AssignTarget
data class NameTarget(val name: String) : AssignTarget()
data class IndexTarget(val obj: Expr, val key: Expr) : AssignTarget()

data class Assign(val targets: List<AssignTarget>, val exprs: List<Expr>) : Stmt()
data class ExprStmt(val expr: Expr) : Stmt()
data class Return(val exprs: List<Expr>) : Stmt()
object Break : Stmt()
data class If(val cond: Expr, val thenBody: List<Stmt>, val elseBody: List<Stmt>?) : Stmt()
data class NumericFor(val varName: String, val start: Expr, val limit: Expr, val step: Expr?, val body: List<Stmt>) : Stmt()
data class GenericFor(val names: List<String>, val exprs: List<Expr>, val body: List<Stmt>) : Stmt()
data class While(val cond: Expr, val body: List<Stmt>) : Stmt()
data class Repeat(val body: List<Stmt>, val cond: Expr) : Stmt()
data class Do(val body: List<Stmt>) : Stmt()

sealed class Expr
data class NumberLit(val value: Double) : Expr()
data class StringLit(val value: String) : Expr()
data class BoolLit(val value: Boolean) : Expr()
object NilLit : Expr()
object Vararg : Expr()
data class VarRef(val name: String) : Expr()
data class IndexExpr(val obj: Expr, val key: Expr) : Expr()
data class Binary(val op: String, val left: Expr, val right: Expr) : Expr()
data class Unary(val op: String, val expr: Expr) : Expr()
data class Call(val callee: Expr, val args: List<Expr>) : Expr()
data class MethodCall(val obj: Expr, val methodName: String, val args: List<Expr>) : Expr()
data class FunctionExpr(val params: List<String>, val isVararg: Boolean, val body: List<Stmt>) : Expr()
data class Paren(val expr: Expr) : Expr()

sealed class TableField
data class PositionalField(val expr: Expr) : TableField()
data class NamedField(val name: String, val expr: Expr) : TableField()
data class KeyedField(val keyExpr: Expr, val valueExpr: Expr) : TableField()
data class TableCtor(val fields: List<TableField>) : Expr()
