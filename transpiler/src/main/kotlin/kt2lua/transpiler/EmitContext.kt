package kt2lua.transpiler

/** Shared emission primitives an emitter feature needs. Unlike parsing, this dispatch is already
 * exhaustiveness-checked by the compiler (`when` over sealed [Statement]/[Expression]), so there
 * is no `supports()`-based registry — [Emitter]'s own `when` just calls into a feature for the
 * cases it owns. */
interface EmitContext {
    val style: EmitStyle
    val types: TypeAnalyzer

    fun line(text: String)
    fun pushIndent()
    fun popIndent()
    fun isDeclBoundary(stmt: Statement): Boolean

    fun emitStatement(stmt: Statement)
    fun emitSubscriptions(decl: ClassDecl)
    fun emitExpression(expr: Expression): String
    fun emitArgList(args: List<CallArg>): String
    fun paramList(fn: FunctionDecl): String
    fun emitFunctionBody(fn: FunctionDecl)

    /** `"List"`/`"Set"`/`"Map"`/a user class name/null — whatever [expr] is tracked as an
     * instance of, so a feature outside [Emitter] (e.g. [kt2lua.transpiler.emitter.ForEmitter])
     * can tell a Set/Map apart from a plain List/Array. */
    fun instanceClassOf(expr: Expression): String?

    /** One member of a module table / `object` body: a `private` function stays a plain local,
     * a non-private function becomes `$moduleVar.<name>`, a variable both stays local and gets
     * mirrored onto `$moduleVar.<name>`, anything else emits normally. */
    fun emitModuleMember(stmt: Statement, moduleVar: String)
}
