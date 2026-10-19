package kt2lua.transpiler

enum class TypeName { INT, DOUBLE, STRING, BOOLEAN, UNIT, ANY }

fun normalizeType(name: String): TypeName = when (name) {
    "Int", "Long", "Short", "Byte" -> TypeName.INT
    "Double", "Float" -> TypeName.DOUBLE
    "String" -> TypeName.STRING
    "Boolean" -> TypeName.BOOLEAN
    "Unit" -> TypeName.UNIT
    else -> TypeName.ANY
}

data class Program(val body: List<Statement>)

sealed class Statement

/** Wildcard imports (`import kotlin.math.*`) are a no-op and never produce one of these — the
 * names they bring in are already recognized as builtins regardless of import. */
data class ImportStatement(val path: String, val alias: String) : Statement()

/** Compiles to a *global* Lua table (not `local`), so independently-transpiled files sharing
 * one Lua environment can see each other's objects without `require()`. */
data class ObjectDecl(val name: String, val body: List<Statement>) : Statement()

/** Always `val`/`var`-prefixed and becomes a field — a plain, non-property constructor
 * parameter isn't supported in this subset. */
data class ClassParam(val name: String, val type: TypeName, val isVar: Boolean)

/** `open`/`override` are parsed and otherwise ignored — Lua's metatable `__index` chain already
 * gives a redeclared method/property in a subclass the right shadowing behavior with no
 * special-casing. Single-superclass inheritance only, no interfaces, no `super` calls.
 * `isData` only changes one thing: an auto-generated `__tostring` when the class doesn't define
 * its own. */
data class ClassDecl(
    val name: String,
    val params: List<ClassParam>,
    val methods: List<FunctionDecl>,
    val isData: Boolean,
    val superClass: String? = null,
    val superArgs: List<CallArg> = emptyList(),
    val properties: List<VariableDecl> = emptyList(),
) : Statement()

/** [rawType] is the type annotation's original identifier text, kept alongside the normalized
 * [type] so the emitter can track a class-typed parameter as an instance for colon-call
 * resolution. [isVararg] is only meaningful on a function's last parameter (enforced at parse
 * time) and compiles to Lua's own `...`. */
data class Param(val name: String, val type: TypeName, val rawType: String = "", val isVararg: Boolean = false, val default: Expression? = null)

sealed class FunctionBody
data class ExprBody(val expr: Expression) : FunctionBody()
data class BlockBody(val block: Block) : FunctionBody()

data class FunctionDecl(
    val name: String,
    val params: List<Param>,
    val returnType: TypeName?,
    val body: FunctionBody,
    /** A `private` top-level function stays a plain local in module-mode output instead of an
     * exported `M.<name>` field. */
    val isPrivate: Boolean = false,
    /** Mirrors [Param.rawType] for the return type, so `val c = someFn()` can be tracked as an
     * instance of a known class. Empty when there's no explicit return type annotation. */
    val rawReturnType: String = "",
    val subscription: Subscription? = null,
) : Statement()

data class Subscription(val event: String? = null)

data class VariableDecl(
    val name: String,
    val varType: TypeName?,
    val mutable: Boolean,
    val init: Expression,
    val isPrivate: Boolean = false,
    val rawType: String = "",
) : Statement()

data class ExpressionStatement(val expr: Expression) : Statement()

/** `val (a, b) = pair` / `val (a, b, c) = dataClassInstance`. Two names destructure via
 * `.first`/`.second` (a Pair, or anything else shaped like one — `partition`/`zip` results);
 * any other case uses `component1()`/`component2()`/... same as a real data class. */
data class DestructuringDecl(val names: List<String>, val mutable: Boolean, val init: Expression, val isPrivate: Boolean = false) : Statement()
data class ReturnStatement(val expr: Expression?) : Statement()
data class IfStatement(val cond: Expression, val thenBranch: Block, val elseBranch: Block?) : Statement()
data class ForStatement(
    val varName: String,
    val start: Expression,
    val end: Expression,
    val inclusive: Boolean,
    val step: Expression?,
    val body: Block,
    val descending: Boolean = false,
) : Statement()
/** Anything after `in` that isn't a `..`/`until` range compiles to `for _, x in ipairs(...)` over
 * a List; [varNames] has more than one entry only for a destructured `for ((k, v) in map)`. */
data class ForInStatement(val varNames: List<String>, val iterable: Expression, val body: Block) : Statement()
data class WhileStatement(val cond: Expression, val body: Block) : Statement()

sealed class AssignTarget
data class NameTarget(val name: String) : AssignTarget()
data class PropertyTarget(val obj: Expression, val name: String) : AssignTarget()
data class IndexTarget(val obj: Expression, val index: Expression) : AssignTarget()

data class Assignment(val target: AssignTarget, val op: String, val expr: Expression) : Statement()
data class Block(val body: List<Statement>) : Statement()

/** `throw SomeException("message")` is special-cased at emit time to throw just the message
 * string, since there's no exception-class hierarchy to call a constructor on; a real declared
 * `class` instance throws that instance value instead (catchable as a table). */
data class ThrowStatement(val expr: Expression) : Statement()

/** At most one `catch` per `try` (unlike real Kotlin's one-per-exception-type) — there's no
 * exception-type hierarchy to match against, so [paramType] is parsed and discarded. */
data class CatchClause(val paramName: String, val body: Block)

/** Compiles to Lua's `pcall`; a `return` inside the try or catch body is propagated to the
 * enclosing function after `finally` has run. */
data class TryStatement(val tryBlock: Block, val catch: CatchClause?, val finallyBlock: Block?) : Statement()

/** [InRangeCondition]/[IsTypeCondition] both require a subject (enforced at parse time), and
 * [IsTypeCondition] is restricted to this subset's primitive [TypeName]s since there's no real
 * runtime type info to check anything else against. */
sealed class WhenCondition
data class ValueCondition(val expr: Expression) : WhenCondition()
data class InRangeCondition(val start: Expression, val end: Expression, val inclusive: Boolean) : WhenCondition()
data class IsTypeCondition(val type: TypeName) : WhenCondition()

data class WhenBranch(val conditions: List<WhenCondition>, val body: Block)

/** Unlike [WhenExpr], `else` is optional here, same as real Kotlin. */
data class WhenStatement(val subject: Expression?, val branches: List<WhenBranch>, val elseBranch: Block?) : Statement()

data class WhenExprBranch(val conditions: List<WhenCondition>, val result: Expression)

sealed class CallArg
data class PositionalArg(val expr: Expression) : CallArg()
data class NamedArg(val name: String, val expr: Expression) : CallArg()

/** Compiles to Lua's `unpack(array)`, which only splats into several arguments in a call's
 * *last* position — so (unlike real Kotlin) this subset requires `*` to be the last argument. */
data class SpreadArg(val expr: Expression) : CallArg()

fun CallArg.toExpression(): Expression = when (this) {
    is PositionalArg -> expr
    is NamedArg -> expr
    is SpreadArg -> expr
}

sealed class Expression
data class IntLiteral(val value: Int) : Expression()
data class DoubleLiteral(val value: Double) : Expression()
data class BooleanLiteral(val value: Boolean) : Expression()
object NullLiteral : Expression()
data class StringTemplateLiteral(val parts: List<TemplateExprPart>) : Expression()

sealed class TemplateExprPart {
    data class Text(val text: String) : TemplateExprPart()
    data class Expr(val expr: Expression) : TemplateExprPart()
}

data class Identifier(val name: String) : Expression()
data class BinaryExpr(val op: String, val left: Expression, val right: Expression) : Expression()
data class UnaryExpr(val op: String, val expr: Expression) : Expression()
data class CallExpr(val callee: String, val args: List<CallArg>) : Expression()
data class PropertyAccessExpr(val obj: Expression, val name: String) : Expression()
/** Always compiles to a plain Lua dot-call, never a colon/self-passing call. */
data class MethodCallExpr(val obj: Expression, val methodName: String, val args: List<CallArg>) : Expression()
/** `::name` — Lua functions already are values, so this just emits the bare name. */
data class FunctionRef(val name: String) : Expression()
data class IfExpr(val cond: Expression, val thenExpr: Expression, val elseExpr: Expression) : Expression()
data class ParenExpr(val expr: Expression) : Expression()

/** One step of a `?.`-containing chain. [safe] = true means this step was reached via `?.` and
 * gets its own nil guard; false means it's a plain `.` partway through the chain (e.g. the `.c`
 * in `a?.b.c`), which does *not* get a runtime nil guard. */
sealed class ChainStep
data class ChainProp(val name: String, val safe: Boolean) : ChainStep()
data class ChainCall(val name: String, val args: List<CallArg>, val safe: Boolean) : ChainStep()

/** Any `.`/`?.` chain containing at least one `?.`; a chain with none compiles to plain nested
 * [PropertyAccessExpr]/[MethodCallExpr] instead. Compiles to an IIFE that walks the chain one
 * step at a time, short-circuiting to `nil` right before any `safe` step. */
data class SafeChainExpr(val base: Expression, val steps: List<ChainStep>) : Expression()

/** Compiles to an explicit `~= nil` check, *not* Lua's `a or b` idiom — that idiom treats Lua
 * `false` the same as `nil`, which would be wrong for `someBoolean ?: default`. */
data class ElvisExpr(val left: Expression, val right: Expression) : Expression()

/** An Elvis guard's early exit; lowered as statements so it exits the containing function. */
data class ReturnExpr(val value: Expression?) : Expression()

/** `expr!!` — raises a runtime error on `nil` via an IIFE, the only way to evaluate the operand
 * once and branch on it inline since Lua has no postfix operator for this. */
data class NotNullAssertExpr(val expr: Expression) : Expression()

/** Empty [params] means an implicit single parameter (`it`), matching real Kotlin. */
data class LambdaExpr(val params: List<String>, val body: Block) : Expression()

/** 0-indexed; compiles to Lua's 1-indexed `arr[(index) + 1]`. Not part of a `?.` chain — there's
 * no Kotlin syntax for a "safe index". */
data class IndexExpr(val obj: Expression, val index: Expression) : Expression()

/** `a to b`, compiling to `{first = ..., second = ...}` — except inside `mapOf(...)`, where the
 * emitter unpacks a list of these directly into `[key] = value` map entries instead. */
data class ToPairExpr(val first: Expression, val second: Expression) : Expression()

/** `a..b`, `a until b` and `a downTo b` used as a value (a `for` header or a `when` condition parses
 * its own range instead). It compiles to a Lua list of the integers in the range, except where the
 * emitter can use the bounds directly (`x in a..b`, `Random.nextInt(a..b)`). */
data class RangeExpr(val start: Expression, val end: Expression, val inclusive: Boolean, val descending: Boolean = false, val step: Expression? = null) : Expression()

/** Requires an explicit `else` since this subset does no exhaustiveness checking. Compiles to
 * an IIFE with `return` per branch, not [IfExpr]'s `cond and a or b` idiom, so it has none of
 * that idiom's falsy-branch bug. */
data class WhenExpr(val subject: Expression?, val branches: List<WhenExprBranch>, val elseResult: Expression) : Expression()
