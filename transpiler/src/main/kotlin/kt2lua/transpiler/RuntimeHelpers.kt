package kt2lua.transpiler

private const val INT_DIV_SOURCE = """local function __intdiv(a, b)
  local q = a / b
  if q >= 0 then
    return math.floor(q)
  else
    return math.ceil(q)
  end
end

"""

private val RECORD_COPY_SOURCE = """
    local function __copyRecord(source, changes)
       local result = {}
       for key, value in pairs(source) do result[key] = value end
       for _, change in ipairs(changes) do result[change.key] = change.value end
       return result
    end

""".trimIndent() + "\n"

private val TAKE_IF_SOURCE = """
    local function __takeIf(value, predicate, invert)
       if predicate(value) ~= invert then return value end
    end

""".trimIndent() + "\n"

// Kotlin's % truncates toward zero (result has the dividend's sign); Lua's % floors (result has
// the divisor's sign). They only disagree when the remainder is nonzero and the signs of a and
// the floor-based remainder differ, in which case subtracting b converts floor-mod to trunc-mod.
private const val MOD_SOURCE = """local function __mod(a, b)
  local r = a % b
  if r ~= 0 and (r < 0) ~= (a < 0) then
    r = r - b
  end
  return r
end

"""

// Lua's ipairs()/# treat a raw `nil` as "end of array", so a List<T?> containing Kotlin nulls
// can't be backed by a plain Lua array with real nil elements - `{1, nil, 3}` silently looks like
// a 1-element array to ipairs(). __NULL is a sentinel stored in place of nil; __nilEncode wraps a
// value going into list storage, __nilDecode unwraps one coming back out.
private const val NULL_SENTINEL_SOURCE = """local __NULL = {}
local function __nilEncode(v) if v == nil then return __NULL else return v end end
local function __nilDecode(v) if v == __NULL then return nil else return v end end

"""

// Lua 5.1-style numbers have no int/float split, so a whole-number Double would print as "5"
// instead of Kotlin's "5.0"; this is only applied where the static type is known to be Double.
private const val FMT_DOUBLE_SOURCE = """local function __fmtDouble(x)
  x = tonumber(x)
  if x ~= x then return "NaN" end
  if x == math.huge then return "Infinity" end
  if x == -math.huge then return "-Infinity" end
  if x == math.floor(x) and math.abs(x) < 1e7 then return string.format("%.1f", x) end
  return tostring(x)
end

"""

private const val TO_INT_SOURCE = """local function __toInt(x)
  x = tonumber(x)
  if x >= 0 then return math.floor(x) else return math.ceil(x) end
end

"""

// A thrown plain message (`throw Exception("boom")` compiles to error("boom")) reaches `catch`
// as a bare string; wrapping it gives `e.message` something to read. A thrown class instance
// (a table) is passed through untouched.
private const val EXCEPTION_SOURCE = """local function __exception(err)
  if type(err) == "table" then return err end
  return setmetatable({message = err}, {__tostring = function(self) return tostring(self.message) end})
end

"""

// Lists are plain Lua arrays, Sets are {list, has} and Maps are {keys, map}; there's no static
// element type to tell a nested collection apart from a class instance, so printing one has to
// look at the table's shape at runtime.
private const val FMT_VALUE_SOURCE = """local function __fmt(v)
  if v == nil or v == __NULL then return "null" end
  if type(v) ~= "table" then return tostring(v) end
  local mt = getmetatable(v)
  if mt and mt.__tostring then return tostring(v) end
  local parts = {}
  if v.list ~= nil and v.has ~= nil then
    for _, e in ipairs(v.list) do table.insert(parts, __fmt(e)) end
    return "[" .. table.concat(parts, ", ") .. "]"
  end
  if v.keys ~= nil and v.map ~= nil then
    for _, k in ipairs(v.keys) do table.insert(parts, __fmt(k) .. "=" .. __fmt(v.map[k])) end
    return "{" .. table.concat(parts, ", ") .. "}"
  end
  if v.first ~= nil and v.second ~= nil and #v == 0 then
    return "(" .. __fmt(v.first) .. ", " .. __fmt(v.second) .. ")"
  end
  for _, e in ipairs(v) do table.insert(parts, __fmt(e)) end
  return "[" .. table.concat(parts, ", ") .. "]"
end

"""

enum class RuntimeHelper(val source: String) {
    RESULT(RESULT_RUNTIME),
    RECORD_COPY(RECORD_COPY_SOURCE),
    TAKE_IF(TAKE_IF_SOURCE),
    INT_DIV(INT_DIV_SOURCE),
    MOD(MOD_SOURCE),
    NULL_SENTINEL(NULL_SENTINEL_SOURCE),
    FMT_VALUE(FMT_VALUE_SOURCE),
    FMT_DOUBLE(FMT_DOUBLE_SOURCE),
    TO_INT(TO_INT_SOURCE),
    EXCEPTION(EXCEPTION_SOURCE),
}
