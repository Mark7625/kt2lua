package kt2lua.transpiler.stdlib.math

import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertTrue

class MathOutputTest {
    private fun emit(body: String): String = transpile("import kotlin.math.*\nfun main() { val x = 2.0; $body }").code

    @Test
    fun `functions Lua already has compile to a direct math call`() {
        val code = emit("val a = asin(x); val b = atan2(x, 3.0); val c = sinh(x); val d = ln(x); val e = x.absoluteValue")
        assertTrue(code.contains("math.asin(x)"))
        assertTrue(code.contains("math.atan2(x, 3.0)"))
        assertTrue(code.contains("math.sinh(x)"))
        assertTrue(code.contains("math.log(x)"))
        assertTrue(code.contains("math.abs(x)"))
    }

    @Test
    fun `functions Lua lacks are built inline from native calls with no helper code`() {
        val code = emit("val a = asinh(x); val b = acosh(x); val c = atanh(x); val d = round(x); val e = x.withSign(-1.0); val f = hypot(x, 3.0)")
        assertTrue(!code.contains("local function __a"), code)
        assertTrue(!code.contains("local function __r"), code)
        assertTrue(code.contains("math.log(x + math.sqrt(x * x - 1))"))
    }

    @Test
    fun `a non-trivial argument is evaluated once through a one-off function`() {
        val code = emit("val y = acosh(x * 2)")
        assertTrue(code.contains("(function(a) return math.log(a + math.sqrt(a * a - 1)) end)((x * 2))"), code)
    }

    @Test
    fun `only the functions that really need a helper pull one in`() {
        assertTrue(emit("val y = cbrt(x)").contains("local function __cbrt"), emit("val y = cbrt(x)"))
        assertTrue(!emit("val y = sqrt(x)").contains("local function __"))
    }
}
