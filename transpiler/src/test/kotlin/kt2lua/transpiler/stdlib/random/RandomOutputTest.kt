package kt2lua.transpiler.stdlib.random

import kt2lua.transpiler.transpile
import kotlin.test.Test
import kotlin.test.assertTrue

class RandomOutputTest {
    private fun emit(source: String): String = transpile(source).code

    @Test
    fun `unseeded Random calls compile to plain math random with no helper code`() {
        val code = emit("import kotlin.random.Random\nfun main() { println(Random.nextInt(1, 7)); println(Random.nextBoolean()) }")
        assertTrue(code.contains("math.random(1, 7 - 1)"))
        assertTrue(!code.contains("__Random"))
    }

    @Test
    fun `a seeded Random pulls in the generator helper`() {
        val code = emit("import kotlin.random.Random\nfun main() { val r = Random(42); println(r.nextInt(10)) }")
        assertTrue(code.contains("__Random.new(42)"))
        assertTrue(code.contains("r:nextInt(10)"))
    }
}
