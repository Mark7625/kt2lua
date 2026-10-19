-- kotlin.time. Lua has no Duration or Instant, so each is a small table with metamethods: a Duration
-- holds whole nanoseconds and an Instant holds epoch seconds plus nanoseconds, and both do their
-- arithmetic, comparison and printing through metamethods, so `a + b`, `a < b` and `println(a)`
-- need no special code at the use site.

local __NS = {NANOSECONDS = 1, MICROSECONDS = 1e3, MILLISECONDS = 1e6, SECONDS = 1e9, MINUTES = 6e10, HOURS = 3.6e12, DAYS = 8.64e13}
local __UNIT_OF = {nanoseconds = "NANOSECONDS", microseconds = "MICROSECONDS", milliseconds = "MILLISECONDS", seconds = "SECONDS", minutes = "MINUTES", hours = "HOURS", days = "DAYS"}
local __WHOLE = {inWholeNanoseconds = 1, inWholeMicroseconds = 1e3, inWholeMilliseconds = 1e6, inWholeSeconds = 1e9, inWholeMinutes = 6e10, inWholeHours = 3.6e12, inWholeDays = 8.64e13}
local __SHORT = {NANOSECONDS = "ns", MICROSECONDS = "us", MILLISECONDS = "ms", SECONDS = "s", MINUTES = "m", HOURS = "h", DAYS = "d"}

local __DurationUnit = {}
for name in pairs(__NS) do __DurationUnit[name] = name end

local __Duration = {}
local __methods = {}

local function __dur(ns)
  if ns == ns and math.abs(ns) ~= math.huge then ns = math.floor(ns + 0.5) end
  return setmetatable({ns = ns}, __Duration)
end

local function __durUnit(x, name)
  if type(x) == "number" then return __dur(x * __NS[__UNIT_OF[name]]) end
  return x[name]
end

local function __toDuration(n, unit) return __dur(n * __NS[unit]) end

__Duration.__index = function(self, key)
  if __methods[key] ~= nil then return __methods[key] end
  if __WHOLE[key] then return (math.modf(self.ns / __WHOLE[key])) end
  if key == "absoluteValue" then return __dur(math.abs(self.ns)) end
end
__Duration.__add = function(a, b) return __dur(a.ns + b.ns) end
__Duration.__sub = function(a, b) return __dur(a.ns - b.ns) end
__Duration.__unm = function(a) return __dur(-a.ns) end
__Duration.__mul = function(a, b)
  if type(a) == "number" then return __dur(a * b.ns) end
  return __dur(a.ns * b)
end
__Duration.__div = function(a, b)
  if type(b) == "table" then return a.ns / b.ns end
  if math.floor(b) == b then return __dur((math.modf(a.ns / b))) end
  return __dur(a.ns / b)
end
__Duration.__eq = function(a, b) return a.ns == b.ns end
__Duration.__lt = function(a, b) return a.ns < b.ns end
__Duration.__le = function(a, b) return a.ns <= b.ns end

-- Splits a into whole multiples of each unit, then the remainder (truncating toward zero).
local function __split(a, units)
  local parts = {}
  for i, unit in ipairs(units) do
    parts[i] = (math.modf(a / unit))
    a = a - parts[i] * unit
  end
  parts[#units + 1] = a
  return parts
end

-- ".123" style fraction of a zero-padded value: trailing zeros dropped, kept in groups of three
-- (unless exact, which keeps 1 and 2 digit fractions as they are).
local function __fraction(value, size, exact)
  if value == 0 then return "" end
  local digits = string.format("%0" .. size .. "d", value)
  local last = size
  while string.sub(digits, last, last) == "0" do last = last - 1 end
  if not (exact and last < 3) then last = math.ceil(last / 3) * 3 end
  return "." .. string.sub(digits, 1, last)
end

local function __fmtDuration(ns)
  if math.abs(ns) == math.huge then return ns > 0 and "Infinity" or "-Infinity" end
  if ns == 0 then return "0s" end
  local p = __split(math.abs(ns), {8.64e13, 3.6e12, 6e10, 1e9})
  local days, hours, minutes, seconds, nanos = p[1], p[2], p[3], p[4], p[5]
  local hasSeconds = seconds ~= 0 or nanos ~= 0
  local parts = {}
  if days ~= 0 then parts[#parts + 1] = string.format("%dd", days) end
  if hours ~= 0 or (days ~= 0 and (minutes ~= 0 or hasSeconds)) then parts[#parts + 1] = string.format("%dh", hours) end
  if minutes ~= 0 or (hasSeconds and (hours ~= 0 or days ~= 0)) then parts[#parts + 1] = string.format("%dm", minutes) end
  if hasSeconds then
    if seconds ~= 0 or #parts > 0 then
      parts[#parts + 1] = string.format("%d", seconds) .. __fraction(nanos, 9, true) .. "s"
    elseif nanos >= 1e6 then
      parts[#parts + 1] = string.format("%d", math.floor(nanos / 1e6)) .. __fraction(nanos % 1e6, 6, true) .. "ms"
    elseif nanos >= 1e3 then
      parts[#parts + 1] = string.format("%d", math.floor(nanos / 1e3)) .. __fraction(nanos % 1e3, 3, true) .. "us"
    else
      parts[#parts + 1] = string.format("%dns", nanos)
    end
  end
  local text = table.concat(parts, " ")
  if ns < 0 then return #parts > 1 and "-(" .. text .. ")" or "-" .. text end
  return text
end
__Duration.__tostring = function(self) return __fmtDuration(self.ns) end

function __methods:toDouble(unit) return self.ns / __NS[unit] end
function __methods:toLong(unit) return (math.modf(self.ns / __NS[unit])) end
__methods.toInt = __methods.toLong
function __methods:isNegative() return self.ns < 0 end
function __methods:isPositive() return self.ns > 0 end
function __methods:isInfinite() return math.abs(self.ns) == math.huge end
function __methods:isFinite() return not self:isInfinite() end

function __methods:toComponents(action, arity)
  local units = ({[2] = {1e9}, [3] = {6e10, 1e9}, [4] = {3.6e12, 6e10, 1e9}})[arity] or {8.64e13, 3.6e12, 6e10, 1e9}
  local p = __split(self.ns, units)
  return action(p[1], p[2], p[3], p[4], p[5])
end

function __methods:toString(unit, decimals)
  if unit == nil then return __fmtDuration(self.ns) end
  local value = self.ns / __NS[unit]
  if math.abs(value) == math.huge then return value > 0 and "Infinity" or "-Infinity" end
  return string.format("%." .. math.min(decimals or 0, 12) .. "f", value) .. __SHORT[unit]
end

function __methods:toIsoString()
  local p = __split(math.abs(self.ns), {3.6e12, 6e10, 1e9})
  local hours, minutes, seconds, nanos = p[1], p[2], p[3], p[4]
  local hasSeconds = seconds ~= 0 or nanos ~= 0
  local text = (self.ns < 0 and "-" or "") .. "PT"
  if hours ~= 0 then text = text .. string.format("%dH", hours) end
  if minutes ~= 0 or (hasSeconds and hours ~= 0) then text = text .. string.format("%dM", minutes) end
  if hasSeconds or (hours == 0 and minutes == 0) then text = text .. string.format("%d", seconds) .. __fraction(nanos, 9, false) .. "S" end
  return text
end

local __DurationCompanion = {ZERO = __dur(0), INFINITE = __dur(math.huge)}
