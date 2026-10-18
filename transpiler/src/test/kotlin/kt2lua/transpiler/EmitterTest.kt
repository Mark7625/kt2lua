package kt2lua.transpiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun emit(source: String): String = transpile(source).code

class EmitterTest {

    @Test
    fun `script mode calls main at the end`() {
        val code = emit("fun main() { println(\"hi\") }")
        assertEquals("local function main()\n   print(\"hi\")\nend\nmain()\n", code)
    }

    @Test
    fun `module mode exports non-private functions and keeps private ones local`() {
        val code = emit(
            """
            fun pub() { return 1 }
            private fun priv() { return 2 }
            """.trimIndent()
        )
        assertEquals(
            "local M = {}\n" +
                "function M.pub()\n" +
                "   return 1\n" +
                "end\n" +
                "\n" +
                "local function priv()\n" +
                "   return 2\n" +
                "end\n" +
                "return M\n",
            code
        )
    }

    @Test
    fun `object-only file emits no module wrapper`() {
        val code = emit("object Foo { fun bar() {} }")
        assertEquals("Foo = {}\nfunction Foo.bar()\nend\n", code)
    }

    @Test
    fun `int divided by int truncates via the intdiv helper`() {
        val code = emit("fun main() { println(7 / 2) }")
        assertTrue(code.contains("local function __intdiv(a, b)"))
        assertTrue(code.contains("print(__intdiv(7, 2))"))
    }

    @Test
    fun `intdiv helper is omitted when never needed`() {
        val code = emit("fun main() { println(7.0 / 2.0) }")
        assertTrue(!code.contains("__intdiv"))
    }

    @Test
    fun `double division is a plain Lua division`() {
        val code = emit("fun main() { val x = 7.0 / 2.0 }")
        assertTrue(code.contains("(7.0 / 2.0)"))
    }

    @Test
    fun `string template wraps non-literal parts in tostring`() {
        val code = emit("""fun main() { val n = 1; println("n is ${'$'}n") }""")
        assertTrue(code.contains("\"n is \" .. tostring(n)"))
    }

    @Test
    fun `if-expression compiles to the and-or idiom`() {
        val code = emit("fun max2(a: Int, b: Int): Int = if (a > b) a else b")
        assertTrue(code.contains("((a > b) and a or b)"))
    }

    @Test
    fun `named-argument call to a lowercase function becomes one table argument`() {
        val code = emit("""fun main() { definePlugin(modules = listOf(1), init = 2) }""")
        assertTrue(code.contains("definePlugin({modules = {1}, init = 2})"))
    }

    @Test
    fun `PascalCase callee with named args emits a bare table, no call wrapper`() {
        val code = emit("""fun main() { val x = Point(x = 1, y = 2) }""")
        assertTrue(code.contains("local x = {x = 1, y = 2}"))
        assertTrue(!code.contains("Point("))
    }

    @Test
    fun `listOf compiles to a Lua table literal`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3) }")
        assertTrue(code.contains("local xs = {1, 2, 3}"))
    }

    @Test
    fun `size property compiles to the length operator`() {
        val code = emit("fun main() { val xs = listOf(1, 2); println(xs.size) }")
        assertTrue(code.contains("print(#xs)"))
    }

    @Test
    fun `collection for-in compiles to ipairs`() {
        val code = emit("fun main() { for (x in listOf(1, 2)) { println(x) } }")
        assertTrue(code.contains("for _, x in ipairs({1, 2}) do"))
    }

    @Test
    fun `cast expression is dropped entirely in the emitted Lua`() {
        val code = emit("""fun main() { val x = require("y") as Any }""")
        assertEquals("local function main()\n   local x = require(\"y\")\nend\nmain()\n", code)
    }

    @Test
    fun `null literal emits nil`() {
        val code = emit("fun main() { val x = null }")
        assertTrue(code.contains("local x = nil"))
    }

    @Test
    fun `math builtins map to the Lua math library`() {
        val code = emit("fun main() { println(sqrt(4.0)); println(abs(-1)); println(max(1, 2)); println(min(1, 2)) }")
        assertTrue(code.contains("math.sqrt(4.0)"))
        assertTrue(code.contains("math.abs((-1))") || code.contains("math.abs(-1)"))
        assertTrue(code.contains("math.max(1, 2)"))
        assertTrue(code.contains("math.min(1, 2)"))
    }

    @Test
    fun `class compiles to the global-table plus new-constructor idiom`() {
        val code = emit("class Point(val x: Int, val y: Int)")
        assertTrue(code.contains("Point = {}"))
        assertTrue(code.contains("Point.__index = Point"))
        assertTrue(code.contains("function Point.new(x, y)"))
        assertTrue(code.contains("local self = setmetatable({}, Point)"))
        assertTrue(code.contains("self.x = x"))
    }

    @Test
    fun `class method is defined with colon syntax`() {
        val code = emit("class Point(val x: Int) { fun getX(): Int = this.x }")
        assertTrue(code.contains("function Point:getX()"))
        assertTrue(code.contains("return self.x"))
    }

    @Test
    fun `constructing an instance compiles to a dot-new call, not a bare table`() {
        val code = emit("""class Point(val x: Int, val y: Int)
            |fun main() { val p = Point(1, 2) }
        """.trimMargin())
        assertTrue(code.contains("local p = Point.new(1, 2)"))
    }

    @Test
    fun `constructing with named args reorders them into constructor-declared order`() {
        val code = emit("""class Point(val x: Int, val y: Int)
            |fun main() { val p = Point(y = 2, x = 1) }
        """.trimMargin())
        assertTrue(code.contains("local p = Point.new(1, 2)"))
    }

    @Test
    fun `method call on a directly constructed instance uses a colon-call`() {
        val code = emit("""class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { val p = Point(1); println(p.show()) }
        """.trimMargin())
        assertTrue(code.contains("p:show()"))
        assertTrue(!code.contains("p.show()"))
    }

    @Test
    fun `method call on an untracked receiver stays a plain dot-call`() {
        val code = emit("""class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { println(somethingElse.show()) }
        """.trimMargin())
        assertTrue(code.contains("somethingElse.show()"))
    }

    @Test
    fun `method call on a class-typed function parameter uses a colon-call`() {
        val code = emit("""class Point(val x: Int) { fun show(): String = "x" }
            |fun greet(p: Point) { println(p.show()) }
        """.trimMargin())
        assertTrue(code.contains("p:show()"))
    }

    @Test
    fun `method returning the same class is tracked so chained calls still colon-call`() {
        val code = emit(
            """class Point(val x: Int) {
            |  fun next(): Point = Point(this.x)
            |  fun show(): String = "x"
            |}
            |fun main() { val a = Point(1); val b = a.next(); println(b.show()) }
            """.trimMargin()
        )
        assertTrue(code.contains("b:show()"))
    }

    @Test
    fun `data class gets an auto-generated __tostring matching Kotlin's toString format`() {
        val code = emit("data class Tag(val label: String, val value: Int)")
        assertTrue(code.contains("Tag.__tostring = function(self) return \"Tag(label=\" .. tostring(self.label) .. \", value=\" .. tostring(self.value) .. \")\" end"))
    }

    @Test
    fun `data class with its own toString does not get an auto-generated one`() {
        val code = emit("""data class Tag(val label: String) { fun toString(): String = "custom" }""")
        assertTrue(!code.contains("__tostring"))
    }

    @Test
    fun `reassigning a variable to a new instance re-tracks its class for later method calls`() {
        val code = emit(
            """class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { var p = Point(1); p = Point(2); println(p.show()) }
            """.trimMargin()
        )
        assertTrue(code.contains("p:show()"))
    }

    @Test
    fun `numeric cast methods are no-ops and toString maps to the Lua stdlib`() {
        val code = emit("fun main() { val x = 5; println(x.toDouble()); println(x.toString()) }")
        assertTrue(code.contains("print(x)"))
        assertTrue(code.contains("print(tostring(x))"))
    }

    @Test
    fun `spread arg compiles to unpack in the final argument position`() {
        val code = emit("fun main() { foo(1, *xs) }")
        assertTrue(code.contains("foo(1, unpack(xs))"))
    }

    @Test
    fun `fold compiles to the runtime helper with the seed as a second argument`() {
        val code = emit("fun main() { val n = xs.fold(0) { acc, x -> acc + x } }")
        assertTrue(code.contains("local function __fold(t, initial, fn)"))
        assertTrue(code.contains("__fold(xs, 0, function(acc, x)"))
    }

    @Test
    fun `reduce compiles to the runtime helper like forEach-map-filter`() {
        val code = emit("fun main() { val n = xs.reduce { acc, x -> acc * x } }")
        assertTrue(code.contains("local function __reduce(t, fn)"))
        assertTrue(code.contains("__reduce(xs, function(acc, x)"))
    }

    @Test
    fun `mapOf with to pairs compiles to a Lua table with bracketed key entries`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1, "b" to 2) }""")
        assertTrue(code.contains("{[\"a\"] = 1, [\"b\"] = 2}"))
    }

    @Test
    fun `standalone to pair compiles to a first-second table`() {
        val code = emit("""fun main() { val p = "x" to 5 }""")
        assertTrue(code.contains("{first = \"x\", second = 5}"))
    }

    @Test
    fun `string-keyed index access is not shifted like a list index`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1); println(m["a"]) }""")
        assertTrue(code.contains("m[\"a\"]"))
        assertTrue(!code.contains("m[(\"a\")"))
    }

    @Test
    fun `string-keyed index assignment is not shifted either`() {
        val code = emit("""fun main() { val m = mapOf("a" to 1); m["b"] = 2 }""")
        assertTrue(code.contains("m[\"b\"] = 2"))
    }

    @Test
    fun `integer index access still gets the list shift alongside map support`() {
        val code = emit("fun main() { val xs = listOf(1, 2, 3); println(xs[0]) }")
        assertTrue(code.contains("xs[(0) + 1]"))
    }

    @Test
    fun `index read shifts the 0-based Kotlin index to Lua's 1-based index`() {
        val code = emit("fun main() { println(xs[0]) }")
        assertTrue(code.contains("xs[(0) + 1]"))
    }

    @Test
    fun `index assignment shifts the index the same way`() {
        val code = emit("fun main() { xs[0] = 5 }")
        assertTrue(code.contains("xs[(0) + 1] = 5"))
    }

    @Test
    fun `nested indexing compiles each index independently`() {
        val code = emit("fun main() { println(matrix[0][1]) }")
        assertTrue(code.contains("matrix[(0) + 1][(1) + 1]"))
    }

    @Test
    fun `property assignment compiles to a plain dotted assignment`() {
        val code = emit("class Point(var x: Int) { fun reset() { this.x = 0 } }")
        assertTrue(code.contains("self.x = 0"))
    }

    @Test
    fun `also returns the receiver, not the lambda result, with it tracking the receiver class`() {
        val code = emit(
            """class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { val p = Point(1).also { println(it.show()) } }
            """.trimMargin()
        )
        assertTrue(code.contains("return it"))
        assertTrue(code.contains("it:show()"))
    }

    @Test
    fun `run executes with this bound to self and returns the block result`() {
        val code = emit(
            """class Point(val x: Int) { }
            |fun main() { val n = Point(5).run { this.x } }
            """.trimMargin()
        )
        assertTrue(code.contains("function(self)"))
        assertTrue(code.contains("return self.x"))
    }

    @Test
    fun `apply executes with this bound to self and returns the receiver`() {
        val code = emit(
            """class Point(var x: Int) { }
            |fun main() { val p = Point(0).apply { this.x = 9 } }
            """.trimMargin()
        )
        assertTrue(code.contains("self.x = 9"))
        assertTrue(code.contains("return self"))
    }

    @Test
    fun `with calls the lambda with self bound to the given receiver`() {
        val code = emit(
            """class Point(val x: Int) { }
            |fun main() { val n = with(Point(5)) { this.x } }
            """.trimMargin()
        )
        assertTrue(code.contains("function(self)"))
        assertTrue(code.contains("return self.x"))
    }

    @Test
    fun `also on a freshly constructed instance still tracks later chained calls`() {
        val code = emit(
            """class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { val p = Point(1).also { println(it.show()) }; println(p.show()) }
            """.trimMargin()
        )
        assertTrue(code.contains("p:show()"))
    }

    @Test
    fun `try-catch compiles to pcall with the error bound in an if-not-ok block`() {
        val code = emit("fun main() { try { risky() } catch (e: Exception) { println(e) } }")
        assertTrue(code.contains("local __ok, __err = pcall(function()"))
        assertTrue(code.contains("if not __ok then"))
        assertTrue(code.contains("local e = __err"))
    }

    @Test
    fun `try-finally with no catch re-raises after finally runs`() {
        val code = emit("fun main() { try { risky() } finally { cleanup() } }")
        assertTrue(code.contains("cleanup()"))
        assertTrue(code.contains("if not __ok then error(__err) end"))
    }

    @Test
    fun `throw with a single string arg extracts just the message`() {
        val code = emit("""fun main() { throw IllegalStateException("bad") }""")
        assertTrue(code.contains("error(\"bad\")"))
        assertTrue(!code.contains("IllegalStateException"))
    }

    @Test
    fun `throwing a real declared class instance throws the instance itself`() {
        val code = emit(
            """class MyError(val reason: String)
            |fun main() { throw MyError("bad") }
            """.trimMargin()
        )
        assertTrue(code.contains("error(MyError.new(\"bad\"))"))
    }

    @Test
    fun `vararg parameter becomes Lua's own dots, bound to a table as the first body line`() {
        val code = emit("fun sum(vararg items: Int): Int { return 0 }\nfun main() { println(sum(1, 2, 3)) }")
        assertTrue(code.contains("function sum(...)"))
        assertTrue(code.contains("local items = {...}"))
    }

    @Test
    fun `let calls the lambda directly against the receiver, no runtime helper`() {
        val code = emit("fun main() { val y = x.let { it * 2 } }")
        assertTrue(code.contains("(function(it)"))
        assertTrue(code.contains(")(x)"))
        assertTrue(!code.contains("__let"))
    }

    @Test
    fun `safe-call let short-circuits to nil like any other safe step`() {
        val code = emit("fun main() { val y = x?.let { it } }")
        assertTrue(code.contains("if __v == nil then return nil end"))
        assertTrue(code.contains(")(__v)"))
    }

    @Test
    fun `lambda with explicit params compiles to a plain Lua function`() {
        val code = emit("fun main() { val f = { a: Int, b: Int -> a + b } }")
        assertTrue(code.contains("function(a, b)"))
        assertTrue(code.contains("return (a + b)"))
    }

    @Test
    fun `implicit it lambda always gets a Lua parameter named it`() {
        val code = emit("fun main() { val f = { it * 2 } }")
        assertTrue(code.contains("function(it)"))
        assertTrue(code.contains("return (it * 2)"))
    }

    @Test
    fun `multi-statement lambda promotes only its last bare expression to a return`() {
        val code = emit("fun main() { xs.forEach { x -> val sq = x * x; println(sq) } }")
        assertTrue(code.contains("local sq = (x * x)"))
        assertTrue(code.contains("return print(sq)"))
    }

    @Test
    fun `forEach compiles to the runtime helper, not a dot or colon call`() {
        val code = emit("fun main() { xs.forEach { println(it) } }")
        assertTrue(code.contains("local function __forEach(t, fn)"))
        assertTrue(code.contains("__forEach(xs, function(it)"))
        assertTrue(!code.contains("xs.forEach") && !code.contains("xs:forEach"))
    }

    @Test
    fun `map and filter each only emit the helper they actually use`() {
        val mapOnly = emit("fun main() { xs.map { it * 2 } }")
        assertTrue(mapOnly.contains("__map"))
        assertTrue(!mapOnly.contains("__filter") && !mapOnly.contains("__forEach"))
    }

    @Test
    fun `forEach lambda over a listOf of a known class tracks it as that class`() {
        val code = emit(
            """class Point(val x: Int) { fun show(): String = "x" }
            |fun main() { val xs = listOf(Point(1), Point(2)); xs.forEach { println(it.show()) } }
            """.trimMargin()
        )
        assertTrue(code.contains("it:show()"))
        assertTrue(!code.contains("it.show()"))
    }

    @Test
    fun `listOf with a mix of classes does not track an element class`() {
        val code = emit(
            """class A(val x: Int) { fun show(): String = "a" }
            |class B(val y: Int) { fun show(): String = "b" }
            |fun main() { val xs = listOf(A(1), B(2)); xs.forEach { println(it.show()) } }
            """.trimMargin()
        )
        assertTrue(code.contains("it.show()"))
    }

    @Test
    fun `a real class method named map takes priority over the collection helper`() {
        val code = emit(
            """class Box(val x: Int) { fun map(f: Int): Int = this.x }
            |fun main() { val b = Box(1); println(b.map(2)) }
            """.trimMargin()
        )
        assertTrue(code.contains("b:map(2)"))
        assertTrue(!code.contains("__map"))
    }

    @Test
    fun `not-null assertion compiles to a runtime error on nil`() {
        val code = emit("fun main() { val a: String? = null; println(a!!.length) }")
        assertTrue(code.contains("if __v == nil then error(\"!! assertion failed: value was null\") end"))
    }

    @Test
    fun `safe call on a nil value short-circuits to nil instead of indexing it`() {
        val code = emit("""fun main() { val a: String? = null; println(a?.length) }""")
        assertTrue(code.contains("if __v == nil then return nil end"))
        assertTrue(code.contains("__v = #__v"))
    }

    @Test
    fun `elvis distinguishes nil from false via an explicit nil check, not the or idiom`() {
        val code = emit("fun main() { val flag = false; println(flag ?: true) }")
        assertTrue(code.contains("if __v ~= nil then return __v end"))
        assertTrue(!code.contains("flag or true"))
    }

    @Test
    fun `string length and collection size both compile to the length operator`() {
        assertTrue(emit("""fun main() { val s = "hi"; println(s.length) }""").contains("#s"))
        assertTrue(emit("fun main() { val xs = listOf(1); println(xs.size) }").contains("#xs"))
    }

    @Test
    fun `non-private top-level val is exported as a module field alongside its local`() {
        val code = emit("val Name = \"mod\"\nfun pub(): String = Name")
        assertTrue(code.contains("local Name = \"mod\""))
        assertTrue(code.contains("M.Name = Name"))
    }

    @Test
    fun `private top-level val stays a plain local, never exported`() {
        val code = emit("private val secret = 1\nfun pub() { return secret }")
        assertTrue(code.contains("local secret = 1"))
        assertTrue(!code.contains("M.secret"))
    }

    @Test
    fun `non-private val inside an object is exported as a field on it too`() {
        val code = emit("object Foo { val Name = \"x\"\nfun bar() {} }")
        assertTrue(code.contains("local Name = \"x\""))
        assertTrue(code.contains("Foo.Name = Name"))
    }

    @Test
    fun `in-range when condition compiles to a bounds check against the subject`() {
        val code = emit("fun main() { when (x) { in 1..5 -> println(1) else -> println(0) } }")
        assertTrue(code.contains("(__subj >= 1 and __subj <= 5)"))
    }

    @Test
    fun `is-type when condition compiles to a runtime type() check`() {
        val code = emit("""fun main() { when (x) { is String -> println(1) else -> println(0) } }""")
        assertTrue(code.contains("(type(__subj) == \"string\")"))
    }

    @Test
    fun `when statement with subject compiles to a scoped if-elseif chain`() {
        val code = emit("fun main() { when (x) { 1, 2 -> println(1) else -> println(0) } }")
        assertTrue(code.contains("local __subj = x"))
        assertTrue(code.contains("if __subj == 1 or __subj == 2 then"))
        assertTrue(code.contains("else"))
    }

    @Test
    fun `subject-less when statement uses conditions directly as booleans`() {
        val code = emit("fun main() { when { x > 0 -> println(1) else -> println(0) } }")
        assertTrue(!code.contains("__subj"))
        assertTrue(code.contains("if (x > 0) then"))
    }

    @Test
    fun `when expression compiles to an immediately-invoked function with explicit returns`() {
        val code = emit("fun main() { val x = when (y) { 1 -> 2 else -> 3 } }")
        assertTrue(code.contains("(function()"))
        assertTrue(code.contains("return 2"))
        assertTrue(code.contains("return 3"))
        assertTrue(code.contains("end)()"))
    }

    @Test
    fun `output is deterministic across repeated transpiles`() {
        val source = "fun add(a: Int, b: Int): Int = a + b\nfun main() { println(add(1, 2)) }"
        assertEquals(emit(source), emit(source))
    }
}
