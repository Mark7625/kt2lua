package kt2lua.interpreter

import kotlin.test.Test
import kotlin.test.assertEquals

private class MapModuleLoader(private val modules: Map<String, String>) : ModuleLoader {
    override fun load(moduleName: String): String? = modules[moduleName]
}

private fun run(source: String, moduleLoader: ModuleLoader? = null): String {
    val out = StringBuilder()
    interpret(source, moduleLoader) { out.append(it) }
    return out.toString()
}

class InterpreterTest {

    @Test
    fun `table constructor, indexing and length`() {
        val output = run(
            """
            local t = {10, 20, 30, name = "widget"}
            print(t[1], t[2], t[3], t.name, #t)
            t[4] = 40
            print(#t)
            """.trimIndent()
        )
        assertEquals("10\t20\t30\twidget\t3\n4\n", output)
    }

    @Test
    fun `closures capture upvalues`() {
        val output = run(
            """
            local function makeCounter()
                local count = 0
                return function()
                    count = count + 1
                    return count
                end
            end
            local c1 = makeCounter()
            local c2 = makeCounter()
            print(c1(), c1(), c1(), c2())
            """.trimIndent()
        )
        assertEquals("1\t2\t3\t1\n", output)
    }

    @Test
    fun `anonymous function stored in a table field and called`() {
        val output = run(
            """
            local M = {
                greet = function(name)
                    print("hello " .. name)
                end,
            }
            M.greet("world")
            """.trimIndent()
        )
        assertEquals("hello world\n", output)
    }

    @Test
    fun `metatable __index implements prototype-style OOP`() {
        val output = run(
            """
            local Animal = {}
            Animal.__index = Animal

            function Animal.new(name)
                local self = setmetatable({}, Animal)
                self.name = name
                return self
            end

            function Animal:speak()
                print(self.name .. " makes a sound")
            end

            local a = Animal.new("Rex")
            a:speak()
            """.trimIndent()
        )
        assertEquals("Rex makes a sound\n", output)
    }

    @Test
    fun `multiple assignment, multiple return, and swap`() {
        val output = run(
            """
            local function minMax(a, b)
                if a < b then return a, b end
                return b, a
            end
            local lo, hi = minMax(9, 3)
            print(lo, hi)
            lo, hi = hi, lo
            print(lo, hi)
            """.trimIndent()
        )
        assertEquals("3\t9\n9\t3\n", output)
    }

    @Test
    fun `pcall catches errors and reports success for normal calls`() {
        val output = run(
            """
            local ok1, err = pcall(function() error("boom") end)
            print(ok1, err)
            local ok2, value = pcall(function() return 42 end)
            print(ok2, value)
            """.trimIndent()
        )
        assertEquals("false\tboom\ntrue\t42\n", output)
    }

    @Test
    fun `pairs and ipairs iterate tables`() {
        val output = run(
            """
            local sum = 0
            for _, v in ipairs({1, 2, 3, 4}) do
                sum = sum + v
            end
            print(sum)

            local keys = {}
            for k, _ in pairs({a = 1, b = 2}) do
                keys[#keys + 1] = k
            end
            table.sort(keys)
            print(table.concat(keys, ","))
            """.trimIndent()
        )
        assertEquals("10\na,b\n", output)
    }

    @Test
    fun `string library and method-call sugar on string values`() {
        val output = run(
            """
            local s = "Hello"
            print(s:upper(), s:sub(2, 3), string.format("%s has %d letters", s, #s))
            """.trimIndent()
        )
        assertEquals("HELLO\tel\tHello has 5 letters\n", output)
    }

    @Test
    fun `break exits loops early`() {
        val output = run(
            """
            local seen = {}
            for i = 1, 10 do
                if i > 3 then break end
                seen[#seen + 1] = i
            end
            print(table.concat(seen, ","))

            local n = 0
            repeat
                n = n + 1
                if n == 2 then break end
            until n >= 5
            print(n)
            """.trimIndent()
        )
        assertEquals("1,2,3\n2\n", output)
    }

    @Test
    fun `varargs are forwarded through a vararg function`() {
        val output = run(
            """
            local function sum(...)
                local total = 0
                for _, v in ipairs({...}) do
                    total = total + v
                end
                return total, select("#", ...)
            end
            print(sum(1, 2, 3, 4))
            """.trimIndent()
        )
        assertEquals("10\t4\n", output)
    }

    @Test
    fun `require loads and caches a sibling module`() {
        val modules = mapOf(
            "xp" to """
                local M = {}
                local total = 0
                function M.add(amount)
                    total = total + amount
                    return total
                end
                return M
            """.trimIndent()
        )
        val output = run(
            """
            local xp = require("xp")
            print(xp.add(5))
            print(xp.add(7))
            local xpAgain = require("xp")
            print(xp == xpAgain)
            """.trimIndent(),
            MapModuleLoader(modules)
        )
        assertEquals("5\n12\ntrue\n", output)
    }

    @Test
    fun `end-to-end plugin shape matching hello-ts's event-subscription pattern`() {
        // Mirrors the real structure seen in hello-ts: a main chunk that requires a module,
        // the module subscribes a handler to a host-provided event table, and the host (here,
        // a stub standing in for the RS3 client) fires the event later.
        val modules = mapOf(
            "xp" to """
                local M = {}
                function M.init()
                    Event.SkillTransmit.Subscribe("xp", function(event)
                        local gained = event.newXP - event.oldXP
                        if gained > 0 then
                            print("skill " .. event.skill .. ": +" .. gained .. " xp")
                        end
                    end)
                end
                return M
            """.trimIndent()
        )
        val main = """
            -- Stub host API standing in for what the RS3 client would provide.
            local handlers = {}
            Event = {
                SkillTransmit = {
                    Subscribe = function(key, handler) handlers[key] = handler end,
                },
            }

            local xp = require("xp")
            xp.init()

            -- Simulate the host firing the event.
            handlers["xp"]({skill = "Attack", oldXP = 100, newXP = 150})
        """.trimIndent()
        val output = run(main, MapModuleLoader(modules))
        assertEquals("skill Attack: +50 xp\n", output)
    }

    @Test
    fun `pcall and error round-trip the original non-string value, not a stringified copy`() {
        val output = run(
            """
            local Foo = {}
            Foo.__index = Foo
            function Foo.new(x)
                local self = setmetatable({}, Foo)
                self.x = x
                return self
            end

            local ok, err = pcall(function()
                error(Foo.new("hello"))
            end)
            print(ok, err.x)
            """.trimIndent()
        )
        assertEquals("false\thello\n", output)
    }

    @Test
    fun `pcall still returns a plain string for a plain string error`() {
        val output = run(
            """
            local ok, err = pcall(function() error("boom") end)
            print(ok, err)
            """.trimIndent()
        )
        assertEquals("false\tboom\n", output)
    }
}
