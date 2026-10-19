-- kotlin.time.Instant and Clock: epoch seconds plus nanoseconds, printed as an ISO-8601 UTC timestamp.

local __Instant = {}
local __instantMethods = {}
__Instant.__index = __instantMethods

local function __instant(seconds, nanos)
  local carry = math.floor(nanos / 1e9)
  seconds = seconds + carry
  return setmetatable({
    epochSeconds = seconds,
    nanosecondsOfSecond = nanos - carry * 1e9,
    isDistantPast = seconds <= -3217862419201,
    isDistantFuture = seconds >= 3093527980800,
  }, __Instant)
end

function __instantMethods:toEpochMilliseconds() return self.epochSeconds * 1000 + math.floor(self.nanosecondsOfSecond / 1e6) end

local function __shift(a, d)
  local whole = (math.modf(d.ns / 1e9))
  return __instant(a.epochSeconds + whole, a.nanosecondsOfSecond + d.ns - whole * 1e9)
end
__Instant.__add = __shift
__Instant.__sub = function(a, b)
  if getmetatable(b) == __Instant then
    return __dur((a.epochSeconds - b.epochSeconds) * 1e9 + a.nanosecondsOfSecond - b.nanosecondsOfSecond)
  end
  return __shift(a, -b)
end
__Instant.__eq = function(a, b) return a.epochSeconds == b.epochSeconds and a.nanosecondsOfSecond == b.nanosecondsOfSecond end
__Instant.__lt = function(a, b)
  return a.epochSeconds < b.epochSeconds or (a.epochSeconds == b.epochSeconds and a.nanosecondsOfSecond < b.nanosecondsOfSecond)
end
__Instant.__le = function(a, b) return not (b < a) end
__Instant.__tostring = function(self)
  return os.date("!%Y-%m-%dT%H:%M:%S", self.epochSeconds) .. __fraction(self.nanosecondsOfSecond, 9, false) .. "Z"
end

local __InstantCompanion = {DISTANT_PAST = __instant(-3217862419201, 0), DISTANT_FUTURE = __instant(3093527980800, 999999999)}
function __InstantCompanion:fromEpochSeconds(seconds, nanos) return __instant(seconds, nanos or 0) end
function __InstantCompanion:fromEpochMilliseconds(ms)
  local seconds = math.floor(ms / 1000)
  return __instant(seconds, (ms - seconds * 1000) * 1e6)
end

local __Clock = {System = {}}
function __Clock.System:now() return __instant(os.time(), 0) end
