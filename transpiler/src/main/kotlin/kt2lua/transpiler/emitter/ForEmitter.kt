package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.ForInStatement
import kt2lua.transpiler.ForStatement
import kt2lua.transpiler.RuntimeHelper
import kt2lua.transpiler.TypeName

/** `for` emission — [ForStatement] (a numeric range) and [ForInStatement] (collection
 * iteration) are two different AST nodes for the same Kotlin keyword, so both live here. */
object ForEmitter {
    fun emitForStatement(stmt: ForStatement, ctx: EmitContext) {
        val startStr = ctx.emitExpression(stmt.start)
        val limitStr = if (stmt.inclusive) ctx.emitExpression(stmt.end) else "(${ctx.emitExpression(stmt.end)} - 1)"
        val stepStr = stmt.step?.let { ctx.emitExpression(it) }
        val header = if (stepStr != null) {
            "for ${stmt.varName} = $startStr, $limitStr, $stepStr do"
        } else {
            "for ${stmt.varName} = $startStr, $limitStr do"
        }
        ctx.line(header)
        ctx.pushIndent()
        ctx.types.pushScope()
        ctx.types.env.declare(stmt.varName, TypeName.INT)
        stmt.body.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end")
    }

    /** `for (x in xs)` over a List iterates the array directly; over a Set, its `.list` field.
     * `for ((k, v) in map)` destructures each entry; a single-name loop over a Map instead binds
     * just the key (closest match to "no destructuring" since there's no single sensible value
     * to bind). */
    fun emitForInStatement(stmt: ForInStatement, ctx: EmitContext) {
        val kind = ctx.instanceClassOf(stmt.iterable)
        ctx.types.pushScope()
        if (kind == "Map") {
            // Hoisted into a local first, unconditionally: the loop body's `$iterableStr.map[...]`
            // line below runs once per iteration, so if the iterable's own text were repeated
            // there (instead of a plain local name), a non-trivial expression — a function call
            // building the map, say — would get re-evaluated on every single iteration.
            ctx.line("local __iter = ${ctx.emitExpression(stmt.iterable)}")
            val keyName = stmt.varNames[0]
            ctx.line("for _, $keyName in ipairs(__iter.keys) do")
            ctx.pushIndent()
            ctx.types.env.declare(keyName, TypeName.ANY)
            if (stmt.varNames.size > 1) {
                val valueName = stmt.varNames[1]
                ctx.types.env.declare(valueName, TypeName.ANY)
                ctx.line("local $valueName = __iter.map[$keyName]")
            }
        } else if (kind == "Sequence") {
            // A Sequence value is itself a factory for a plain Lua iterator function (see
            // Emitter.emitSequenceExpr) — exactly the shape `for x in f do ... end` already
            // expects, so no `ipairs`/`.list` unwrapping is needed here at all.
            val varName = stmt.varNames[0]
            ctx.line("for $varName in (${ctx.emitExpression(stmt.iterable)})() do")
            ctx.pushIndent()
            ctx.types.env.declare(varName, TypeName.ANY)
        } else {
            val iterableStr = ctx.emitExpression(stmt.iterable)
            val varName = stmt.varNames[0]
            val source = if (kind == "Set") "$iterableStr.list" else iterableStr
            ctx.line("for _, $varName in ipairs($source) do")
            ctx.pushIndent()
            ctx.types.env.declare(varName, TypeName.ANY)
            if (kind == "List" || kind == "Set") {
                ctx.useRuntimeHelper(RuntimeHelper.NULL_SENTINEL)
                ctx.line("$varName = __nilDecode($varName)")
            }
        }
        stmt.body.body.forEach { ctx.emitStatement(it) }
        ctx.types.popScope()
        ctx.popIndent()
        ctx.line("end")
    }
}
