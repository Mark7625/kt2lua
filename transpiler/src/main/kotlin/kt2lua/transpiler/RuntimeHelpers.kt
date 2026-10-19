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

// Lua 5.1-style numbers have no int/float split, so a Double has to be formatted from its static
// type: Kotlin prints the shortest digits that round-trip ("3.141592653589793", "5.0", "1.0E-4"),
// switching to scientific notation below 1e-3 and from 1e7 up.
private const val FMT_DOUBLE_SOURCE = """local function __fmtDouble(x)
  x = tonumber(x)
  if x ~= x then return "NaN" end
  if x == math.huge then return "Infinity" end
  if x == -math.huge then return "-Infinity" end
  if x == 0 then
    if 1 / x < 0 then return "-0.0" end
    return "0.0"
  end
  local e
  for p = 0, 16 do
    e = string.format("%." .. p .. "e", x)
    if tonumber(e) == x then break end
  end
  local sign = ""
  if string.sub(e, 1, 1) == "-" then
    sign = "-"
    e = string.sub(e, 2)
  end
  local epos = string.find(e, "e", 1, true)
  local mantissa = string.sub(e, 1, epos - 1)
  local exp = tonumber(string.sub(e, epos + 1))
  local digits = string.sub(mantissa, 1, 1) .. string.sub(mantissa, 3)
  if exp >= -3 and exp < 7 then
    local intPart, fracPart
    if exp >= 0 then
      if #digits < exp + 1 then digits = digits .. string.rep("0", exp + 1 - #digits) end
      intPart = string.sub(digits, 1, exp + 1)
      fracPart = string.sub(digits, exp + 2)
      if fracPart == "" then fracPart = "0" end
    else
      intPart = "0"
      fracPart = string.rep("0", -exp - 1) .. digits
    end
    return sign .. intPart .. "." .. fracPart
  end
  local frac = string.sub(digits, 2)
  if frac == "" then frac = "0" end
  return sign .. string.sub(digits, 1, 1) .. "." .. frac .. "E" .. exp
end

"""

// kotlin.math helpers that have no single Lua equivalent. round() ties go to the even neighbour
// like Kotlin's, sign()/truncate() keep -0.0 and NaN, log10 falls back for runtimes without it.
private const val MATH_SOURCE = """local function __round(x)
  if x ~= x or x == math.huge or x == -math.huge then return x end
  local f = math.floor(x)
  local d = x - f
  local r
  if d < 0.5 then r = f
  elseif d > 0.5 then r = f + 1
  elseif f % 2 == 0 then r = f
  else r = f + 1 end
  if r == 0 and x < 0 then return -0.0 end
  return r
end
local function __sign(x)
  if x ~= x then return x end
  if x > 0 then return 1 end
  if x < 0 then return -1 end
  return x
end
local function __truncate(x)
  if x ~= x or x == math.huge or x == -math.huge then return x end
  local r
  if x >= 0 then r = math.floor(x) else r = math.ceil(x) end
  if r == 0 and x < 0 then return -0.0 end
  return r
end
local function __log10(x)
  if math.log10 then return math.log10(x) end
  return math.log(x) / math.log(10)
end
local function __hypot(a, b)
  return math.sqrt(a * a + b * b)
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
    MATH(MATH_SOURCE),
}
