package kt2lua.interpreter

/** Registers the globals and `math`/`string`/`table`/`os` libraries into a fresh [Environment]. */
internal fun installStdlib(interp: Interpreter, globals: Environment, onOutput: (String) -> Unit) {
    globals.define("print", native("print") { args ->
        onOutput(args.joinToString("\t") { interp.tostring(it) } + "\n")
        null
    })
    globals.define("tostring", native("tostring") { args -> interp.tostring(args.getOrNull(0)) })
    globals.define("tonumber", native("tonumber") { args ->
        when (val v = args.getOrNull(0)) {
            is Double -> v
            is String -> {
                val base = (args.getOrNull(1) as? Double)?.toInt()
                if (base != null) v.trim().toLongOrNull(base)?.toDouble() else v.trim().toDoubleOrNull()
            }
            else -> null
        }
    })
    globals.define("type", native("type") { args -> typeName(args.getOrNull(0)) })
    globals.define("require", native("require") { args -> interp.require(args[0] as String).firstOrNull() })

    globals.define("error", NativeFunction("error") { args ->
        // Real Lua's `error(v)` accepts any value, not just strings — a table standing in for
        // a custom exception is the common non-string case. Only a string value gets stringified
        // (trivially, it already is one); anything else is carried through as-is so `pcall`'s
        // caller gets the original value back, not `tostring(v)`'s rendering of it.
        val value = args.getOrNull(0)
        throw LuaRuntimeException(interp.tostring(value), value)
    })
    globals.define("assert", NativeFunction("assert") { args ->
        if (!isTruthy(args.getOrNull(0))) {
            val value = args.getOrNull(1)
            throw LuaRuntimeException(value?.let { interp.tostring(it) } ?: "assertion failed!", value ?: "assertion failed!")
        }
        args
    })
    globals.define("pcall", NativeFunction("pcall") { args ->
        try {
            listOf(true) + interp.callValue(args.getOrNull(0), args.drop(1))
        } catch (e: LuaRuntimeException) {
            listOf(false, e.value)
        }
    })
    globals.define("xpcall", NativeFunction("xpcall") { args ->
        val fn = args.getOrNull(0)
        val handler = args.getOrNull(1)
        try {
            listOf(true) + interp.callValue(fn, args.drop(2))
        } catch (e: LuaRuntimeException) {
            listOf(false) + interp.callValue(handler, listOf(e.value))
        }
    })

    globals.define("select", NativeFunction("select") { args ->
        val which = args.getOrNull(0)
        val rest = args.drop(1)
        if (which == "#") listOf(rest.size.toDouble())
        else {
            val n = asNumber(which).toInt()
            if (n < 0) rest.takeLast(-n) else rest.drop(n - 1)
        }
    })

    globals.define("rawget", native("rawget") { args -> (args[0] as LuaTable).rawGet(args.getOrNull(1)) })
    globals.define("rawset", native("rawset") { args ->
        (args[0] as LuaTable).rawSet(args.getOrNull(1), args.getOrNull(2)); args[0]
    })
    globals.define("rawequal", native("rawequal") { args -> args.getOrNull(0) == args.getOrNull(1) })
    globals.define("rawlen", native("rawlen") { args ->
        when (val v = args.getOrNull(0)) {
            is LuaTable -> v.length().toDouble()
            is String -> v.length.toDouble()
            else -> throw LuaRuntimeException("table or string expected")
        }
    })

    globals.define("setmetatable", native("setmetatable") { args ->
        val table = args[0] as LuaTable
        table.metatable = args.getOrNull(1) as LuaTable?
        table
    })
    globals.define("getmetatable", native("getmetatable") { args -> (args.getOrNull(0) as? LuaTable)?.metatable })

    val nextFn = NativeFunction("next") { args -> luaNext(args[0] as LuaTable, args.getOrNull(1)) }
    globals.define("next", nextFn)
    globals.define("pairs", NativeFunction("pairs") { args -> listOf(nextFn, args.getOrNull(0), null) })
    globals.define("ipairs", NativeFunction("ipairs") { args -> listOf(IPAIRS_ITER, args.getOrNull(0), 0.0) })
    globals.define("unpack", tableUnpackNative())

    installMath(globals)
    installCoroutines(interp, globals)
    installString(globals)
    installTable(globals)
    installOs(globals)

    val ioTable = LuaTable()
    ioTable.rawSet("write", native("write") { args ->
        args.forEach { onOutput(interp.tostring(it)) }
        null
    })
    globals.define("io", ioTable)
}

private fun luaNext(table: LuaTable, key: Any?): List<Any?> {
    val keys = table.entries.keys.toList()
    val startIndex = if (key == null) 0 else keys.indexOf(key) + 1
    if (key != null && startIndex == 0) throw LuaRuntimeException("invalid key to 'next'")
    if (startIndex >= keys.size) return listOf(null)
    val k = keys[startIndex]
    return listOf(k, table.entries[k])
}

private val IPAIRS_ITER = NativeFunction("ipairs_iterator") { args ->
    val table = args[0] as LuaTable
    val i = (args[1] as Double).toInt() + 1
    val value = table.rawGet(i.toDouble())
    if (value == null) listOf(null) else listOf(i.toDouble(), value)
}

/** `native` returning a single value can't express "two values or nil"; this wraps that case. */
private fun tableUnpackNative(): NativeFunction = NativeFunction("unpack") { args ->
    val table = args[0] as LuaTable
    val i = (args.getOrNull(1) as? Double)?.toInt() ?: 1
    val j = (args.getOrNull(2) as? Double)?.toInt() ?: table.length()
    (i..j).map { table.rawGet(it.toDouble()) }
}

private fun installMath(globals: Environment) {
    val math = LuaTable()
    math.rawSet("pi", Math.PI)
    math.rawSet("huge", Double.POSITIVE_INFINITY)
    math.rawSet("sqrt", native("sqrt") { a -> Math.sqrt(asNumber(a[0])) })
    math.rawSet("abs", native("abs") { a -> Math.abs(asNumber(a[0])) })
    math.rawSet("floor", native("floor") { a -> Math.floor(asNumber(a[0])) })
    math.rawSet("ceil", native("ceil") { a -> Math.ceil(asNumber(a[0])) })
    math.rawSet("max", native("max") { a -> a.maxOf { asNumber(it) } })
    math.rawSet("min", native("min") { a -> a.minOf { asNumber(it) } })
    math.rawSet("pow", native("pow") { a -> Math.pow(asNumber(a[0]), asNumber(a[1])) })
    math.rawSet("fmod", native("fmod") { a ->
        asNumber(a[0]) % asNumber(a[1])
    })
    math.rawSet("modf", NativeFunction("modf") { a ->
        val v = asNumber(a[0])
        val ip = if (v >= 0) Math.floor(v) else Math.ceil(v)
        listOf(ip, v - ip)
    })
    math.rawSet("sin", native("sin") { a -> Math.sin(asNumber(a[0])) })
    math.rawSet("cos", native("cos") { a -> Math.cos(asNumber(a[0])) })
    math.rawSet("tan", native("tan") { a -> Math.tan(asNumber(a[0])) })
    math.rawSet("exp", native("exp") { a -> Math.exp(asNumber(a[0])) })
    math.rawSet("log", native("log") { a ->
        val x = asNumber(a[0])
        val base = (a.getOrNull(1) as? Double)
        if (base != null) Math.log(x) / Math.log(base) else Math.log(x)
    })
    math.rawSet("log10", native("log10") { a -> Math.log10(asNumber(a[0])) })
    math.rawSet("asin", native("asin") { a -> Math.asin(asNumber(a[0])) })
    math.rawSet("acos", native("acos") { a -> Math.acos(asNumber(a[0])) })
    math.rawSet("atan", native("atan") { a -> Math.atan(asNumber(a[0])) })
    math.rawSet("atan2", native("atan2") { a -> Math.atan2(asNumber(a[0]), asNumber(a[1])) })
    math.rawSet("sinh", native("sinh") { a -> Math.sinh(asNumber(a[0])) })
    math.rawSet("cosh", native("cosh") { a -> Math.cosh(asNumber(a[0])) })
    math.rawSet("tanh", native("tanh") { a -> Math.tanh(asNumber(a[0])) })
    math.rawSet("ldexp", native("ldexp") { a -> Math.scalb(asNumber(a[0]), asNumber(a[1]).toInt()) })
    math.rawSet("frexp", NativeFunction("frexp") { a ->
        val x = asNumber(a[0])
        if (x == 0.0 || x.isNaN() || x.isInfinite()) {
            listOf(x, 0.0)
        } else {
            val scale = if (Math.abs(x) < java.lang.Double.MIN_NORMAL) 54 else 0
            val scaled = Math.scalb(x, scale)
            val e = Math.getExponent(scaled) + 1
            listOf(Math.scalb(scaled, -e), (e - scale).toDouble())
        }
    })
    val random = java.util.Random()
    math.rawSet("randomseed", native("randomseed") { a -> random.setSeed(asNumber(a[0]).toLong()); null })
    math.rawSet("random", native("random") { a ->
        when (a.size) {
            0 -> random.nextDouble()
            1 -> (1 + random.nextInt(asNumber(a[0]).toInt())).toDouble()
            else -> {
                val lo = asNumber(a[0]).toLong()
                val hi = asNumber(a[1]).toLong()
                (lo + java.util.concurrent.ThreadLocalRandom.current().nextLong(hi - lo + 1)).toDouble()
            }
        }
    })
    globals.define("math", math)
}

private fun installString(globals: Environment) {
    val string = LuaTable()
    string.rawSet("len", native("len") { a -> (a[0] as String).length.toDouble() })
    string.rawSet("upper", native("upper") { a -> (a[0] as String).uppercase() })
    string.rawSet("lower", native("lower") { a -> (a[0] as String).lowercase() })
    string.rawSet("reverse", native("reverse") { a -> (a[0] as String).reversed() })
    string.rawSet("rep", native("rep") { a -> (a[0] as String).repeat(asNumber(a[1]).toInt()) })
    string.rawSet("byte", native("byte") { a ->
        val s = a[0] as String
        val i = ((a.getOrNull(1) as? Double)?.toInt() ?: 1).let { if (it < 0) s.length + it + 1 else it }
        if (i in 1..s.length) s[i - 1].code.toDouble() else null
    })
    string.rawSet("char", native("char") { a -> a.map { asNumber(it).toInt().toChar() }.joinToString("") })
    string.rawSet("sub", native("sub") { a ->
        val s = a[0] as String
        val len = s.length
        fun resolve(idx: Int, default: Int): Int = when {
            idx == 0 -> default
            idx > 0 -> idx
            else -> maxOf(len + idx + 1, 1)
        }
        var i = resolve((a.getOrNull(1) as? Double)?.toInt() ?: 1, 1)
        var j = resolve((a.getOrNull(2) as? Double)?.toInt() ?: -1, len)
        if (i < 1) i = 1
        if (j > len) j = len
        if (i > j) "" else s.substring(i - 1, j)
    })
    // NOTE: find/gsub here do plain substring search-and-replace only — real Lua "patterns"
    // (%a, %d, character classes, captures, anchors, ...) are not implemented.
    string.rawSet("find", NativeFunction("find") { a ->
        val s = a[0] as String
        val needle = a[1] as String
        val from = ((a.getOrNull(2) as? Double)?.toInt() ?: 1).let { if (it < 1) 1 else it }
        val idx = s.indexOf(needle, from - 1)
        if (idx < 0) listOf(null) else listOf((idx + 1).toDouble(), (idx + needle.length).toDouble())
    })
    string.rawSet("gsub", NativeFunction("gsub") { a ->
        val s = a[0] as String
        val needle = a[1] as String
        val replacement = a[2] as String
        if (needle.isEmpty()) listOf(s, 0.0)
        else listOf(s.replace(needle, replacement), s.split(needle).size.toDouble() - 1)
    })
    string.rawSet("format", native("format") { a -> luaFormat(a[0] as String, a.drop(1)) })
    globals.define("string", string)
}

private fun luaFormat(fmt: String, args: List<Any?>): String {
    val sb = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < fmt.length) {
        val c = fmt[i]
        if (c != '%') {
            sb.append(c); i++; continue
        }
        val start = i
        i++
        while (i < fmt.length && fmt[i] in "-+ #0") i++
        while (i < fmt.length && fmt[i].isDigit()) i++
        if (i < fmt.length && fmt[i] == '.') {
            i++
            while (i < fmt.length && fmt[i].isDigit()) i++
        }
        if (i >= fmt.length) {
            sb.append(fmt.substring(start)); break
        }
        val conv = fmt[i]
        val spec = fmt.substring(start, i + 1)
        i++
        when (conv) {
            '%' -> sb.append('%')
            'd', 'i' -> sb.append(String.format(spec.dropLast(1) + "d", asNumber(args[argIndex++]).toLong()))
            'f', 'g', 'e' -> sb.append(String.format(spec, asNumber(args[argIndex++])))
            'x', 'X', 'o' -> sb.append(String.format(spec, asNumber(args[argIndex++]).toLong()))
            's' -> sb.append(String.format(spec, args[argIndex++].let { if (it is String) it else luaToString(it) }))
            'q' -> sb.append('"').append(args[argIndex++].toString().replace("\"", "\\\"")).append('"')
            else -> sb.append(spec)
        }
    }
    return sb.toString()
}

private fun installTable(globals: Environment) {
    val table = LuaTable()
    table.rawSet("insert", NativeFunction("insert") { a ->
        val t = a[0] as LuaTable
        if (a.size >= 3) {
            val pos = asNumber(a[1]).toInt()
            val len = t.length()
            var k = len
            while (k >= pos) {
                t.rawSet((k + 1).toDouble(), t.rawGet(k.toDouble()))
                k--
            }
            t.rawSet(pos.toDouble(), a[2])
        } else {
            t.rawSet((t.length() + 1).toDouble(), a.getOrNull(1))
        }
        emptyList()
    })
    table.rawSet("remove", native("remove") { a ->
        val t = a[0] as LuaTable
        val len = t.length()
        if (len == 0) return@native null
        val pos = (a.getOrNull(1) as? Double)?.toInt() ?: len
        val removed = t.rawGet(pos.toDouble())
        for (k in pos until len) t.rawSet(k.toDouble(), t.rawGet((k + 1).toDouble()))
        t.rawSet(len.toDouble(), null)
        removed
    })
    table.rawSet("concat", native("concat") { a ->
        val t = a[0] as LuaTable
        val sep = a.getOrNull(1) as? String ?: ""
        val i = (a.getOrNull(2) as? Double)?.toInt() ?: 1
        val j = (a.getOrNull(3) as? Double)?.toInt() ?: t.length()
        (i..j).joinToString(sep) { luaToString(t.rawGet(it.toDouble())) }
    })
    table.rawSet("sort", NativeFunction("sort") { a ->
        val t = a[0] as LuaTable
        val comparator = a.getOrNull(1)
        val len = t.length()
        val items = (1..len).map { t.rawGet(it.toDouble()) }.toMutableList()
        val sorted = if (comparator != null) {
            items.sortedWith { x, y ->
                val less = isTruthy((comparator as LuaCallable).call(listOf(x, y)).firstOrNull())
                if (less) -1 else {
                    val greater = isTruthy(comparator.call(listOf(y, x)).firstOrNull())
                    if (greater) 1 else 0
                }
            }
        } else {
            items.sortedWith { x, y ->
                if (x is Double && y is Double) x.compareTo(y) else (x as String).compareTo(y as String)
            }
        }
        sorted.forEachIndexed { idx, v -> t.rawSet((idx + 1).toDouble(), v) }
        emptyList()
    })
    table.rawSet("unpack", tableUnpackNative())
    globals.define("table", table)
}

private fun installOs(globals: Environment) {
    val os = LuaTable()
    os.rawSet("time", native("time") { System.currentTimeMillis() / 1000.0 })
    os.rawSet("clock", native("clock") { System.nanoTime() / 1_000_000_000.0 })
    os.rawSet("date", native("date") { a ->
        var format = a.getOrNull(0) as? String ?: "%c"
        val seconds = (a.getOrNull(1) as? Double) ?: (System.currentTimeMillis() / 1000.0)
        val utc = format.startsWith("!")
        if (utc) format = format.substring(1)
        val zone = if (utc) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault()
        val moment = java.time.Instant.ofEpochSecond(Math.floor(seconds).toLong()).atZone(zone)
        if (format.startsWith("*t")) {
            val table = LuaTable()
            table.rawSet("year", moment.year.toDouble())
            table.rawSet("month", moment.monthValue.toDouble())
            table.rawSet("day", moment.dayOfMonth.toDouble())
            table.rawSet("hour", moment.hour.toDouble())
            table.rawSet("min", moment.minute.toDouble())
            table.rawSet("sec", moment.second.toDouble())
            table.rawSet("wday", (moment.dayOfWeek.value % 7 + 1).toDouble())
            table.rawSet("yday", moment.dayOfYear.toDouble())
            table.rawSet("isdst", false)
            table
        } else {
            val out = StringBuilder()
            var i = 0
            while (i < format.length) {
                val c = format[i]
                if (c != '%' || i + 1 >= format.length) { out.append(c); i++; continue }
                val f = format[i + 1]
                i += 2
                when (f) {
                    'Y' -> out.append("%04d".format(moment.year))
                    'y' -> out.append("%02d".format(moment.year % 100))
                    'm' -> out.append("%02d".format(moment.monthValue))
                    'd' -> out.append("%02d".format(moment.dayOfMonth))
                    'H' -> out.append("%02d".format(moment.hour))
                    'M' -> out.append("%02d".format(moment.minute))
                    'S' -> out.append("%02d".format(moment.second))
                    'j' -> out.append("%03d".format(moment.dayOfYear))
                    'p' -> out.append(if (moment.hour < 12) "AM" else "PM")
                    'A' -> out.append(moment.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH))
                    'a' -> out.append(moment.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
                    'B' -> out.append(moment.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH))
                    'b' -> out.append(moment.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
                    '%' -> out.append('%')
                    else -> out.append('%').append(f)
                }
            }
            out.toString()
        }
    })
    os.rawSet("exit", native("exit") { a ->
        val code = when (val v = a.getOrNull(0)) {
            null, true -> 0
            false -> 1
            else -> asNumber(v).toInt()
        }
        throw LuaExitException(code)
    })
    globals.define("os", os)
}
