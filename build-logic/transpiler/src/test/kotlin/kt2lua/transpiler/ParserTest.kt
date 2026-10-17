package kt2lua.transpiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun parse(source: String): Program = Parser(Lexer(source).tokenize()).parseProgram()

class ParserTest {

    @Test
    fun `parses a function with block body and explicit return type`() {
        val program = parse("fun add(a: Int, b: Int): Int { return a + b }")
        val fn = assertIs<FunctionDecl>(program.body[0])
        assertEquals("add", fn.name)
        assertEquals(listOf(Param("a", TypeName.INT, "Int"), Param("b", TypeName.INT, "Int")), fn.params)
        assertEquals(TypeName.INT, fn.returnType)
        assertIs<BlockBody>(fn.body)
    }

    @Test
    fun `parses an expression-bodied function`() {
        val program = parse("fun square(x: Int): Int = x * x")
        val fn = assertIs<FunctionDecl>(program.body[0])
        val body = assertIs<ExprBody>(fn.body)
        val mul = assertIs<BinaryExpr>(body.expr)
        assertEquals("*", mul.op)
    }

    @Test
    fun `private modifier marks the function private`() {
        val program = parse("private fun helper() {}")
        val fn = assertIs<FunctionDecl>(program.body[0])
        assertTrue(fn.isPrivate)
    }

    @Test
    fun `binary operator precedence - multiplication binds tighter than addition`() {
        val program = parse("val x = 1 + 2 * 3")
        val decl = assertIs<VariableDecl>(program.body[0])
        val add = assertIs<BinaryExpr>(decl.init)
        assertEquals("+", add.op)
        assertIs<IntLiteral>(add.left)
        val mul = assertIs<BinaryExpr>(add.right)
        assertEquals("*", mul.op)
    }

    @Test
    fun `relational and equality chain left-to-right under logical operators`() {
        val program = parse("val x = a < b && b == c")
        val decl = assertIs<VariableDecl>(program.body[0])
        val and = assertIs<BinaryExpr>(decl.init)
        assertEquals("&&", and.op)
        assertEquals("<", (and.left as BinaryExpr).op)
        assertEquals("==", (and.right as BinaryExpr).op)
    }

    @Test
    fun `if-else parses as a statement with both branches`() {
        val program = parse("fun f() { if (x > 0) { return 1 } else { return 2 } }")
        val fn = assertIs<FunctionDecl>(program.body[0])
        val body = assertIs<BlockBody>(fn.body)
        val ifStmt = assertIs<IfStatement>(body.block.body[0])
        assertEquals(1, ifStmt.thenBranch.body.size)
        assertTrue(ifStmt.elseBranch != null)
    }

    @Test
    fun `if used as an expression requires both branches`() {
        val program = parse("val x = if (a > b) a else b")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertIs<IfExpr>(decl.init)
    }

    @Test
    fun `numeric for range parses inclusive and exclusive bounds`() {
        val inclusive = assertIs<ForStatement>(parse("fun f() { for (i in 1..5) {} }").let {
            (it.body[0] as FunctionDecl).let { fn -> (fn.body as BlockBody).block.body[0] }
        })
        assertTrue(inclusive.inclusive)

        val exclusive = assertIs<ForStatement>(parse("fun f() { for (i in 1 until 5) {} }").let {
            (it.body[0] as FunctionDecl).let { fn -> (fn.body as BlockBody).block.body[0] }
        })
        assertTrue(!exclusive.inclusive)
    }

    @Test
    fun `collection for-in parses distinctly from a numeric range`() {
        val program = parse("fun f() { for (x in items) { println(x) } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = (fn.body as BlockBody).block.body[0]
        assertIs<ForInStatement>(stmt)
    }

    @Test
    fun `property access chain nests left-associatively`() {
        val program = parse("val x = a.b.c")
        val decl = assertIs<VariableDecl>(program.body[0])
        val outer = assertIs<PropertyAccessExpr>(decl.init)
        assertEquals("c", outer.name)
        val inner = assertIs<PropertyAccessExpr>(outer.obj)
        assertEquals("b", inner.name)
    }

    @Test
    fun `method call with named arguments`() {
        val program = parse("""val x = foo.bar(a = 1, b = "two")""")
        val decl = assertIs<VariableDecl>(program.body[0])
        val call = assertIs<MethodCallExpr>(decl.init)
        assertEquals("bar", call.methodName)
        assertEquals(2, call.args.size)
        assertIs<NamedArg>(call.args[0])
    }

    @Test
    fun `function reference via double colon`() {
        val program = parse("val x = ::doIt")
        val decl = assertIs<VariableDecl>(program.body[0])
        val ref = assertIs<FunctionRef>(decl.init)
        assertEquals("doIt", ref.name)
    }

    @Test
    fun `object declaration collects its body statements`() {
        val program = parse("object Foo { fun a() {} private fun b() {} }")
        val obj = assertIs<ObjectDecl>(program.body[0])
        assertEquals("Foo", obj.name)
        assertEquals(2, obj.body.size)
        assertTrue((obj.body[1] as FunctionDecl).isPrivate)
    }

    @Test
    fun `wildcard import is dropped entirely`() {
        val program = parse("import kotlin.math.*\nfun f() {}")
        assertEquals(1, program.body.size)
        assertIs<FunctionDecl>(program.body[0])
    }

    @Test
    fun `specific import becomes an ImportStatement with the last segment as alias`() {
        val program = parse("import modules.xp\nfun f() {}")
        val imp = assertIs<ImportStatement>(program.body[0])
        assertEquals("modules/xp", imp.path)
        assertEquals("xp", imp.alias)
    }

    @Test
    fun `import with explicit alias`() {
        val program = parse("import modules.xp as x\nfun f() {}")
        val imp = assertIs<ImportStatement>(program.body[0])
        assertEquals("x", imp.alias)
    }

    @Test
    fun `null literal parses on its own`() {
        val program = parse("val x = null")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals(NullLiteral, decl.init)
    }

    @Test
    fun `nullable type annotation is accepted and normalized`() {
        val program = parse("var x: Foo? = null")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals(TypeName.ANY, decl.varType)
    }

    @Test
    fun `cast expression is parsed and does not change the underlying expression`() {
        val program = parse("val x = require(\"y\") as SomeType")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertIs<CallExpr>(decl.init)
    }

    @Test
    fun `safe cast as-question is also accepted`() {
        val program = parse("val x = y as? SomeType")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertIs<Identifier>(decl.init)
    }

    @Test
    fun `malformed input raises a parse exception with position`() {
        val ex = assertFailsWith<ParseException> { parse("fun (") }
        assertTrue(ex.line >= 1)
    }

    @Test
    fun `unterminated block raises a parse exception`() {
        assertFailsWith<ParseException> { parse("fun f() { ") }
    }

    @Test
    fun `return with no expression parses as a bare return`() {
        val program = parse("fun f() { return }")
        val fn = program.body[0] as FunctionDecl
        val ret = assertIs<ReturnStatement>((fn.body as BlockBody).block.body[0])
        assertNull(ret.expr)
    }

    @Test
    fun `const val is parsed as a plain variable declaration`() {
        val program = parse("""const val Name: String = "hello"""")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals("Name", decl.name)
    }

    @Test
    fun `private modifier on a top-level val is recorded on the declaration`() {
        val program = parse("private val secret = 1")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertTrue(decl.isPrivate)
    }

    @Test
    fun `top-level val without a modifier is not private`() {
        val program = parse("val x = 1")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertTrue(!decl.isPrivate)
    }

    @Test
    fun `reserved but unimplemented keywords raise a clear diagnostic instead of mis-parsing`() {
        for (source in listOf(
            "interface Foo {}",
            "enum class Foo { A, B }",
            "sealed class Foo {}",
            "abstract class Foo {}",
            "super.foo()",
        )) {
            val ex = assertFailsWith<ParseException>("expected failure for: $source") { parse(source) }
            assertTrue(ex.message!!.contains("not supported"), "unexpected message for '$source': ${ex.message}")
        }
    }

    @Test
    fun `modifier keywords remain callable as real function and variable names`() {
        // Regression test for a real bug: a plugin calling its own open() method (a completely
        // ordinary function name) was rejected as "Kotlin 'open' is not supported", because
        // `open` was briefly treated as a hard-reserved keyword instead of the modifier
        // (soft) keyword it actually is in real Kotlin.
        val program = parse("fun f() { open() }")
        val fn = program.body[0] as FunctionDecl
        assertIs<ExpressionStatement>((fn.body as BlockBody).block.body[0])
        parse("fun open() {}")
        parse("val enum = 1")
        parse("val override = 2")
    }

    @Test
    fun `soft keywords like 'init' remain valid identifiers outside class bodies`() {
        val program = parse("""fun main() { definePlugin(init = 2) }""")
        val fn = assertIs<FunctionDecl>(program.body[0])
        assertIs<ExpressionStatement>((fn.body as BlockBody).block.body[0])
    }

    @Test
    fun `class with a primary constructor and a method parses into a ClassDecl`() {
        val program = parse("class Point(val x: Int, val y: Int) { fun len(): Int = x }")
        val decl = assertIs<ClassDecl>(program.body[0])
        assertEquals("Point", decl.name)
        assertEquals(listOf(ClassParam("x", TypeName.INT, false), ClassParam("y", TypeName.INT, false)), decl.params)
        assertEquals(1, decl.methods.size)
        assertTrue(!decl.isData)
    }

    @Test
    fun `data class sets isData without needing data to be a reserved word`() {
        val program = parse("data class Tag(val label: String)")
        val decl = assertIs<ClassDecl>(program.body[0])
        assertTrue(decl.isData)
    }

    @Test
    fun `data used as a plain identifier elsewhere is unaffected`() {
        val program = parse("val data = 1")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals("data", decl.name)
    }

    @Test
    fun `var constructor parameter is recorded as mutable`() {
        val program = parse("class Box(var contents: Int)")
        val decl = assertIs<ClassDecl>(program.body[0])
        assertTrue(decl.params[0].isVar)
    }

    @Test
    fun `plain non-property constructor parameter is rejected with a clear message`() {
        val ex = assertFailsWith<ParseException> { parse("class Point(x: Int)") }
        assertTrue(ex.message!!.contains("val") && ex.message!!.contains("var"))
    }

    @Test
    fun `a non-constructor property declaration in a class body is accepted`() {
        val program = parse("class Foo(val x: Int) { val y = 1 }")
        val decl = assertIs<ClassDecl>(program.body[0])
        assertEquals(1, decl.properties.size)
        assertEquals("y", decl.properties[0].name)
    }

    @Test
    fun `a non fun-val-var statement in a class body is rejected`() {
        val ex = assertFailsWith<ParseException> { parse("class Foo(val x: Int) { return 1 }") }
        assertTrue(ex.message!!.contains("fun"))
    }

    @Test
    fun `safe call chain with no safe step folds to the plain nested property-access shape`() {
        val program = parse("val x = a.b.c")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertIs<PropertyAccessExpr>(decl.init)
    }

    @Test
    fun `chain with a safe step becomes a flat SafeChainExpr`() {
        val program = parse("val x = a?.b.c")
        val decl = assertIs<VariableDecl>(program.body[0])
        val chain = assertIs<SafeChainExpr>(decl.init)
        assertEquals(2, chain.steps.size)
        assertTrue((chain.steps[0] as ChainProp).safe)
        assertTrue(!(chain.steps[1] as ChainProp).safe)
    }

    @Test
    fun `safe method call step is distinguished from a safe property step`() {
        val program = parse("val x = a?.b()")
        val decl = assertIs<VariableDecl>(program.body[0])
        val chain = assertIs<SafeChainExpr>(decl.init)
        assertIs<ChainCall>(chain.steps[0])
    }

    @Test
    fun `infix to parses as a ToPairExpr`() {
        val program = parse("""val p = "a" to 1""")
        val decl = assertIs<VariableDecl>(program.body[0])
        val pair = assertIs<ToPairExpr>(decl.init)
        assertIs<StringTemplateLiteral>(pair.first)
        assertIs<IntLiteral>(pair.second)
    }

    @Test
    fun `to used as a plain identifier elsewhere is unaffected`() {
        val program = parse("val to = 1")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals("to", decl.name)
    }

    @Test
    fun `to binds looser than additive but tighter than elvis`() {
        val program = parse("val x = a + 1 to b ?: c")
        val decl = assertIs<VariableDecl>(program.body[0])
        val elvis = assertIs<ElvisExpr>(decl.init)
        val pair = assertIs<ToPairExpr>(elvis.left)
        assertIs<BinaryExpr>(pair.first)
    }

    @Test
    fun `index expression parses the object and index`() {
        val program = parse("val x = xs[0]")
        val decl = assertIs<VariableDecl>(program.body[0])
        val index = assertIs<IndexExpr>(decl.init)
        assertEquals("xs", (index.obj as Identifier).name)
        assertIs<IntLiteral>(index.index)
    }

    @Test
    fun `chained and nested indexing parse correctly`() {
        val program = parse("val x = matrix[0][1]")
        val decl = assertIs<VariableDecl>(program.body[0])
        val outer = assertIs<IndexExpr>(decl.init)
        assertIs<IndexExpr>(outer.obj)
    }

    @Test
    fun `index assignment parses as an Assignment with an IndexTarget`() {
        val program = parse("fun f() { xs[0] = 5 }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<Assignment>((fn.body as BlockBody).block.body[0])
        val target = assertIs<IndexTarget>(stmt.target)
        assertEquals("xs", (target.obj as Identifier).name)
    }

    @Test
    fun `property assignment parses as an Assignment with a PropertyTarget`() {
        val program = parse("fun f() { this.x = 5 }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<Assignment>((fn.body as BlockBody).block.body[0])
        val target = assertIs<PropertyTarget>(stmt.target)
        assertEquals("x", target.name)
        assertEquals("self", (target.obj as Identifier).name)
    }

    @Test
    fun `plain name assignment still parses as an Assignment with a NameTarget`() {
        val program = parse("fun f() { x = 5 }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<Assignment>((fn.body as BlockBody).block.body[0])
        assertEquals("x", (stmt.target as NameTarget).name)
    }

    @Test
    fun `compound property assignment parses too`() {
        val program = parse("fun f() { this.x += 1 }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<Assignment>((fn.body as BlockBody).block.body[0])
        assertEquals("+=", stmt.op)
    }

    @Test
    fun `a bare call expression statement is not mistaken for an assignment`() {
        val program = parse("fun f() { foo() }")
        val fn = program.body[0] as FunctionDecl
        assertIs<ExpressionStatement>((fn.body as BlockBody).block.body[0])
    }

    @Test
    fun `try-catch-finally parses the catch param and all three blocks`() {
        val program = parse("fun f() { try { a() } catch (e: Exception) { b() } finally { c() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<TryStatement>((fn.body as BlockBody).block.body[0])
        assertEquals(1, stmt.tryBlock.body.size)
        val catch = stmt.catch!!
        assertEquals("e", catch.paramName)
        assertEquals(1, catch.body.body.size)
        assertEquals(1, stmt.finallyBlock!!.body.size)
    }

    @Test
    fun `try with only finally and no catch is allowed`() {
        val program = parse("fun f() { try { a() } finally { b() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<TryStatement>((fn.body as BlockBody).block.body[0])
        assertEquals(null, stmt.catch)
        assertTrue(stmt.finallyBlock != null)
    }

    @Test
    fun `try with neither catch nor finally is rejected`() {
        val ex = assertFailsWith<ParseException> { parse("fun f() { try { a() } }") }
        assertTrue(ex.message!!.contains("catch") && ex.message!!.contains("finally"))
    }

    @Test
    fun `throw parses the thrown expression`() {
        val program = parse("""fun f() { throw IllegalStateException("bad") }""")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<ThrowStatement>((fn.body as BlockBody).block.body[0])
        assertIs<CallExpr>(stmt.expr)
    }

    @Test
    fun `catch and finally used as plain identifiers elsewhere are unaffected`() {
        val program = parse("fun f() { val catch = 1; val finally = 2 }")
        val fn = program.body[0] as FunctionDecl
        assertEquals(2, (fn.body as BlockBody).block.body.size)
    }

    @Test
    fun `spread arg parses as a SpreadArg`() {
        val program = parse("fun main() { foo(*xs) }")
        val fn = program.body[0] as FunctionDecl
        val call = assertIs<CallExpr>(((fn.body as BlockBody).block.body[0] as ExpressionStatement).expr)
        assertIs<SpreadArg>(call.args[0])
    }

    @Test
    fun `spread arg not in last position is rejected`() {
        val ex = assertFailsWith<ParseException> { parse("fun main() { foo(*xs, 1) }") }
        assertTrue(ex.message!!.contains("last argument"))
    }

    @Test
    fun `spread arg as the sole or final argument is accepted`() {
        parse("fun main() { foo(*xs) }")
        parse("fun main() { foo(1, *xs) }")
    }

    @Test
    fun `vararg parameter is recorded on the last param`() {
        val program = parse("fun f(prefix: String, vararg items: Int) {}")
        val fn = program.body[0] as FunctionDecl
        assertTrue(!fn.params[0].isVararg)
        assertTrue(fn.params[1].isVararg)
    }

    @Test
    fun `vararg on a non-last parameter is rejected`() {
        val ex = assertFailsWith<ParseException> { parse("fun f(vararg items: Int, last: String) {}") }
        assertTrue(ex.message!!.contains("vararg"))
    }

    @Test
    fun `vararg used as a plain identifier elsewhere is unaffected`() {
        val program = parse("val vararg = 1")
        val decl = assertIs<VariableDecl>(program.body[0])
        assertEquals("vararg", decl.name)
    }

    @Test
    fun `lambda with explicit params parses their names, discarding any type annotation`() {
        val program = parse("val f = { a: Int, b: Int -> a + b }")
        val decl = assertIs<VariableDecl>(program.body[0])
        val lambda = assertIs<LambdaExpr>(decl.init)
        assertEquals(listOf("a", "b"), lambda.params)
    }

    @Test
    fun `lambda with no declared params has an empty param list for the implicit it`() {
        val program = parse("val f = { it * 2 }")
        val decl = assertIs<VariableDecl>(program.body[0])
        val lambda = assertIs<LambdaExpr>(decl.init)
        assertTrue(lambda.params.isEmpty())
    }

    @Test
    fun `a bare expression that merely starts like a param name is not mistaken for one`() {
        val program = parse("val f = { x + 1 }")
        val decl = assertIs<VariableDecl>(program.body[0])
        val lambda = assertIs<LambdaExpr>(decl.init)
        assertTrue(lambda.params.isEmpty())
        assertEquals(1, lambda.body.body.size)
    }

    @Test
    fun `trailing lambda with no parens becomes the call's sole argument`() {
        val program = parse("fun main() { xs.forEach { println(it) } }")
        val fn = program.body[0] as FunctionDecl
        val call = assertIs<MethodCallExpr>(((fn.body as BlockBody).block.body[0] as ExpressionStatement).expr)
        assertEquals("forEach", call.methodName)
        assertEquals(1, call.args.size)
        assertIs<LambdaExpr>(call.args[0].toExpression())
    }

    @Test
    fun `trailing lambda after explicit parens becomes the final argument`() {
        val program = parse("fun main() { xs.fold(0) { acc, x -> acc + x } }")
        val fn = program.body[0] as FunctionDecl
        val call = assertIs<MethodCallExpr>(((fn.body as BlockBody).block.body[0] as ExpressionStatement).expr)
        assertEquals(2, call.args.size)
        assertIs<IntLiteral>(call.args[0].toExpression())
        assertIs<LambdaExpr>(call.args[1].toExpression())
    }

    @Test
    fun `not-null assertion parses as a dedicated expression node`() {
        val program = parse("val x = a!!")
        val decl = assertIs<VariableDecl>(program.body[0])
        val assertExpr = assertIs<NotNullAssertExpr>(decl.init)
        assertEquals("a", (assertExpr.expr as Identifier).name)
    }

    @Test
    fun `not-null assertion can be followed by further chain access`() {
        val program = parse("val x = a!!.b")
        val decl = assertIs<VariableDecl>(program.body[0])
        val access = assertIs<PropertyAccessExpr>(decl.init)
        assertIs<NotNullAssertExpr>(access.obj)
    }

    @Test
    fun `elvis parses as a dedicated expression node`() {
        val program = parse("val x = a ?: b")
        val decl = assertIs<VariableDecl>(program.body[0])
        val elvis = assertIs<ElvisExpr>(decl.init)
        assertEquals("a", (elvis.left as Identifier).name)
        assertEquals("b", (elvis.right as Identifier).name)
    }

    @Test
    fun `elvis binds looser than additive but tighter than comparison`() {
        val program = parse("val x = a + 1 ?: 0 > 5")
        val decl = assertIs<VariableDecl>(program.body[0])
        val gt = assertIs<BinaryExpr>(decl.init)
        assertEquals(">", gt.op)
        assertIs<ElvisExpr>(gt.left)
    }

    @Test
    fun `when statement with subject parses branches, comma conditions, and else`() {
        val program = parse("fun f() { when (x) { 1, 2 -> foo() else -> bar() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<WhenStatement>((fn.body as BlockBody).block.body[0])
        assertEquals("x", (stmt.subject as Identifier).name)
        assertEquals(1, stmt.branches.size)
        assertEquals(2, stmt.branches[0].conditions.size)
        assertTrue(stmt.elseBranch != null)
    }

    @Test
    fun `subject-less when statement treats conditions as booleans`() {
        val program = parse("fun f() { when { x > 0 -> foo() else -> bar() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<WhenStatement>((fn.body as BlockBody).block.body[0])
        assertEquals(null, stmt.subject)
    }

    @Test
    fun `when expression requires an else branch`() {
        val ex = assertFailsWith<ParseException> { parse("val x = when (y) { 1 -> 2 }") }
        assertTrue(ex.message!!.contains("else"))
    }

    @Test
    fun `when branch with an in-range condition`() {
        val program = parse("fun f() { when (x) { in 1..5 -> foo() else -> bar() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<WhenStatement>((fn.body as BlockBody).block.body[0])
        val cond = assertIs<InRangeCondition>(stmt.branches[0].conditions[0])
        assertTrue(cond.inclusive)
    }

    @Test
    fun `in-range when condition without a subject is rejected`() {
        val ex = assertFailsWith<ParseException> { parse("fun f() { when { in 1..5 -> foo() } }") }
        assertTrue(ex.message!!.contains("subject"))
    }

    @Test
    fun `when branch with an is-type condition for a supported primitive`() {
        val program = parse("fun f() { when (x) { is String -> foo() else -> bar() } }")
        val fn = program.body[0] as FunctionDecl
        val stmt = assertIs<WhenStatement>((fn.body as BlockBody).block.body[0])
        val cond = assertIs<IsTypeCondition>(stmt.branches[0].conditions[0])
        assertEquals(TypeName.STRING, cond.type)
    }

    @Test
    fun `is-type when condition for an unsupported type is rejected with a clear message`() {
        val ex = assertFailsWith<ParseException> { parse("fun f() { when (x) { is Foo -> bar() } }") }
        assertTrue(ex.message!!.contains("not supported"))
    }

    @Test
    fun `when expression parses with a value and an else`() {
        val program = parse("val x = when (y) { 1 -> 2 else -> 3 }")
        val decl = assertIs<VariableDecl>(program.body[0])
        val whenExpr = assertIs<WhenExpr>(decl.init)
        assertEquals(1, whenExpr.branches.size)
        assertIs<IntLiteral>(whenExpr.elseResult)
    }
}
