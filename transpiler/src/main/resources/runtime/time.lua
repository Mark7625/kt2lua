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
  if ns == ns and ns ~= math.huge and ns ~= -math.huge then ns = math.floor(ns + 0.5) end
  return setmetatable({ns = ns}, __Duration)
end

local function __durUnit(x, name)
  if type(x) == "number" then return __dur(x * __NS[__UNIT_OF[name]]) end
  return x[name]
end

local function __toDuration(n, unit) return __dur(n * __NS[unit]) end

__Duration.__index = function(self, key)
  local m = __methods[key]
  if m ~= nil then return m end
  local whole = __WHOLE[key]
  if whole then return (math.modf(self.ns / whole)) end
  if key == "absoluteValue" then return __dur(math.abs(self.ns)) end
  return nil
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

local function __fmtFraction(whole, fraction, size, unit)
  local s = string.format("%d", whole)
  if fraction ~= 0 then
    local digits = string.format("%0" .. size .. "d", fraction)
    local last = size
    while last > 0 and string.sub(digits, last, last) == "0" do last = last - 1 end
    if last >= 3 then last = math.ceil(last / 3) * 3 end
    s = s .. "." .. string.sub(digits, 1, last)
  end
  return s .. unit
end

local function __fmtDuration(ns)
  if ns == math.huge then return "Infinity" end
  if ns == -math.huge then return "-Infinity" end
  if ns == 0 then return "0s" end
  local a = math.abs(ns)
  local days = math.floor(a / 8.64e13); a = a - days * 8.64e13
  local hours = math.floor(a / 3.6e12); a = a - hours * 3.6e12
  local minutes = math.floor(a / 6e10); a = a - minutes * 6e10
  local seconds = math.floor(a / 1e9)
  local nanos = a - seconds * 1e9
  local hasDays, hasHours, hasMinutes = days ~= 0, hours ~= 0, minutes ~= 0
  local hasSeconds = seconds ~= 0 or nanos ~= 0
  local parts = {}
  if hasDays then parts[#parts + 1] = string.format("%dd", days) end
  if hasHours or (hasDays and (hasMinutes or hasSeconds)) then parts[#parts + 1] = string.format("%dh", hours) end
  if hasMinutes or (hasSeconds and (hasHours or hasDays)) then parts[#parts + 1] = string.format("%dm", minutes) end
  if hasSeconds then
    if seconds ~= 0 or hasDays or hasHours or hasMinutes then
      parts[#parts + 1] = __fmtFraction(seconds, nanos, 9, "s")
    elseif nanos >= 1e6 then
      parts[#parts + 1] = __fmtFraction(math.floor(nanos / 1e6), nanos % 1e6, 6, "ms")
    elseif nanos >= 1e3 then
      parts[#parts + 1] = __fmtFraction(math.floor(nanos / 1e3), nanos % 1e3, 3, "us")
    else
      parts[#parts + 1] = string.format("%dns", nanos)
    end
  end
  local text = table.concat(parts, " ")
  if ns < 0 then
    if #parts > 1 then return "-(" .. text .. ")" end
    return "-" .. text
  end
  return text
end
__Duration.__tostring = function(self) return __fmtDuration(self.ns) end

function __methods:toDouble(unit) return self.ns / __NS[unit] end
function __methods:toLong(unit) return (math.modf(self.ns / __NS[unit])) end
__methods.toInt = __methods.toLong
function __methods:isNegative() return self.ns < 0 end
function __methods:isPositive() return self.ns > 0 end
function __methods:isInfinite() return self.ns == math.huge or self.ns == -math.huge end
function __methods:isFinite() return not self:isInfinite() end
function __methods:compareTo(other)
  if self.ns < other.ns then return -1 end
  if self.ns > other.ns then return 1 end
  return 0
end
function __methods:plus(other) return self + other end
function __methods:minus(other) return self - other end
function __methods:times(n) return self * n end
function __methods:div(n) return self / n end
function __methods:unaryMinus() return -self end
function __methods:coerceAtLeast(other) if self < other then return other end return self end
function __methods:coerceAtMost(other) if self > other then return other end return self end
function __methods:coerceIn(low, high)
  if self < low then return low end
  if self > high then return high end
  return self
end

function __methods:toComponents(action, arity)
  local rest = self.ns
  local function take(unit)
    local q = (math.modf(rest / unit))
    rest = rest - q * unit
    return q
  end
  if arity == 2 then
    local s = take(1e9)
    return action(s, rest)
  elseif arity == 3 then
    local m = take(6e10)
    local s = take(1e9)
    return action(m, s, rest)
  elseif arity == 4 then
    local h = take(3.6e12)
    local m = take(6e10)
    local s = take(1e9)
    return action(h, m, s, rest)
  end
  local d = take(8.64e13)
  local h = take(3.6e12)
  local m = take(6e10)
  local s = take(1e9)
  return action(d, h, m, s, rest)
end

function __methods:toString(unit, decimals)
  if unit == nil then return __fmtDuration(self.ns) end
  local value = self.ns / __NS[unit]
  if value == math.huge or value == -math.huge then return value > 0 and "Infinity" or "-Infinity" end
  return string.format("%." .. math.min(decimals or 0, 12) .. "f", value) .. __SHORT[unit]
end

function __methods:toIsoString()
  local ns = self.ns
  local a = math.abs(ns)
  local hours = math.floor(a / 3.6e12); a = a - hours * 3.6e12
  local minutes = math.floor(a / 6e10); a = a - minutes * 6e10
  local seconds = math.floor(a / 1e9)
  local nanos = a - seconds * 1e9
  local hasHours = hours ~= 0
  local hasSeconds = seconds ~= 0 or nanos ~= 0
  local hasMinutes = minutes ~= 0 or (hasSeconds and hasHours)
  local text = (ns < 0 and "-" or "") .. "PT"
  if hasHours then text = text .. string.format("%dH", hours) end
  if hasMinutes then text = text .. string.format("%dM", minutes) end
  if hasSeconds or (not hasHours and not hasMinutes) then
    text = text .. string.format("%d", seconds)
    if nanos ~= 0 then
      local digits = string.format("%09d", nanos)
      local last = 9
      while string.sub(digits, last, last) == "0" do last = last - 1 end
      text = text .. "." .. string.sub(digits, 1, math.ceil(last / 3) * 3)
    end
    text = text .. "S"
  end
  return text
end

local __DurationCompanion = {ZERO = __dur(0), INFINITE = __dur(math.huge)}

-- Time marks and sources. A source only has to say what time it is, in nanoseconds.

local __Mark = {}
__Mark.__index = __Mark
local function __mark(source, at) return setmetatable({source = source, at = at}, __Mark) end
function __Mark:elapsedNow() return __dur(self.source:readNs() - self.at) end
function __Mark:hasPassedNow() return self.source:readNs() >= self.at end
function __Mark:hasNotPassedNow() return self.source:readNs() < self.at end
function __Mark:plus(d) return __mark(self.source, self.at + d.ns) end
function __Mark:minus(other)
  if getmetatable(other) == __Mark then return __dur(self.at - other.at) end
  return __mark(self.source, self.at - other.ns)
end
__Mark.__add = function(a, b) return a:plus(b) end
__Mark.__sub = function(a, b) return a:minus(b) end
__Mark.__eq = function(a, b) return a.at == b.at end
__Mark.__lt = function(a, b) return a.at < b.at end
__Mark.__le = function(a, b) return a.at <= b.at end

local __Monotonic = {}
function __Monotonic:readNs() return os.clock() * 1e9 end
function __Monotonic:markNow() return __mark(self, self:readNs()) end
local __TimeSource = {Monotonic = __Monotonic}

local __TestTimeSource = {}
__TestTimeSource.__index = __TestTimeSource
function __TestTimeSource.new() return setmetatable({t = 0}, __TestTimeSource) end
function __TestTimeSource:readNs() return self.t end
function __TestTimeSource:markNow() return __mark(self, self:readNs()) end
function __TestTimeSource:plusAssign(d) self.t = self.t + d.ns end
__TestTimeSource.__add = function(source, d)
  source.t = source.t + d.ns
  return source
end

AbstractLongTimeSource = {}
AbstractLongTimeSource.__index = AbstractLongTimeSource
function AbstractLongTimeSource.new(unit) return setmetatable({__unit = __NS[unit]}, AbstractLongTimeSource) end
function AbstractLongTimeSource:readNs() return self:read() * self.__unit end
function AbstractLongTimeSource:markNow() return __mark(self, self:readNs()) end
AbstractDoubleTimeSource = {}
AbstractDoubleTimeSource.__index = AbstractDoubleTimeSource
function AbstractDoubleTimeSource.new(unit) return setmetatable({__unit = __NS[unit]}, AbstractDoubleTimeSource) end
function AbstractDoubleTimeSource:readNs() return self:read() * self.__unit end
function AbstractDoubleTimeSource:markNow() return __mark(self, self:readNs()) end

-- Instant and Clock

local __Instant = {}
local __instantMethods = {}

local function __instant(seconds, nanos)
  local carry = math.floor(nanos / 1e9)
  return setmetatable({s = seconds + carry, n = nanos - carry * 1e9}, __Instant)
end

local __DISTANT_PAST = __instant(-3217862419201, 0)
local __DISTANT_FUTURE = __instant(3093527980800, 999999999)

__Instant.__index = function(self, key)
  local m = __instantMethods[key]
  if m ~= nil then return m end
  if key == "epochSeconds" then return self.s end
  if key == "nanosecondsOfSecond" then return self.n end
  if key == "isDistantPast" then return self.s <= __DISTANT_PAST.s end
  if key == "isDistantFuture" then return self.s >= __DISTANT_FUTURE.s end
  return nil
end
function __instantMethods:toEpochMilliseconds() return self.s * 1000 + math.floor(self.n / 1e6) end
function __instantMethods:plus(d)
  local whole = (math.modf(d.ns / 1e9))
  return __instant(self.s + whole, self.n + (d.ns - whole * 1e9))
end
function __instantMethods:minus(other)
  if getmetatable(other) == __Instant then return __dur((self.s - other.s) * 1e9 + (self.n - other.n)) end
  return self:plus(-other)
end
__Instant.__add = function(a, b) return a:plus(b) end
__Instant.__sub = function(a, b) return a:minus(b) end
__Instant.__eq = function(a, b) return a.s == b.s and a.n == b.n end
__Instant.__lt = function(a, b) return a.s < b.s or (a.s == b.s and a.n < b.n) end
__Instant.__le = function(a, b) return a.s < b.s or (a.s == b.s and a.n <= b.n) end
__Instant.__tostring = function(self)
  local text = os.date("!%Y-%m-%dT%H:%M:%S", self.s)
  if self.n ~= 0 then
    local digits = string.format("%09d", self.n)
    local last = 9
    while string.sub(digits, last, last) == "0" do last = last - 1 end
    text = text .. "." .. string.sub(digits, 1, math.ceil(last / 3) * 3)
  end
  return text .. "Z"
end

local __InstantCompanion = {DISTANT_PAST = __DISTANT_PAST, DISTANT_FUTURE = __DISTANT_FUTURE}
function __InstantCompanion:fromEpochSeconds(seconds, nanos) return __instant(seconds, nanos or 0) end
function __InstantCompanion:fromEpochMilliseconds(ms)
  local seconds = math.floor(ms / 1000)
  return __instant(seconds, (ms - seconds * 1000) * 1e6)
end

local __Clock = {System = {}}
function __Clock.System:now() return __instant(os.time(), 0) end

local function __asClock(source, origin)
  local start = source:markNow()
  return {now = function() return origin + start:elapsedNow() end}
end
__Monotonic.asClock = __asClock
__TestTimeSource.asClock = __asClock
AbstractLongTimeSource.asClock = __asClock
AbstractDoubleTimeSource.asClock = __asClock
