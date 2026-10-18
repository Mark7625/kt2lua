package kt2lua.transpiler.emitter

import kt2lua.transpiler.EmitContext
import kt2lua.transpiler.ObjectDecl

/** `object { ... }` emission — see [ObjectDecl]'s doc comment (in `Ast.kt`) for the module-style
 * table this produces. */
object ObjectEmitter {
    fun emitObjectDecl(decl: ObjectDecl, ctx: EmitContext) {
        ctx.line("${decl.name} = {}")
        decl.body.forEachIndexed { i, stmt ->
            if (i > 0 && ctx.isDeclBoundary(stmt)) ctx.line("")
            ctx.emitModuleMember(stmt, decl.name)
        }
    }
}
