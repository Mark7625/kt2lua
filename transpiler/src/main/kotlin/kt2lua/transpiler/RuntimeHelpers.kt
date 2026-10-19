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

enum class RuntimeHelper(val source: String) {
    RESULT(RESULT_RUNTIME),
    RECORD_COPY(RECORD_COPY_SOURCE),
    TAKE_IF(TAKE_IF_SOURCE),
    INT_DIV(INT_DIV_SOURCE),
}
