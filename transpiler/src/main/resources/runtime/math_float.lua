-- kotlin.math ulp / nextUp / nextDown / nextTowards. Lua can't read a double's bits, so the
-- spacing between neighbouring doubles is derived from math.frexp.

local __MIN_SUBNORMAL = 2 ^ -1074

local function __ulp(x)
  if x ~= x then return x end
  x = math.abs(x)
  if x == math.huge then return x end
  if x == 0 then return __MIN_SUBNORMAL end
  local _, e = math.frexp(x)
  return math.max(2 ^ (e - 53), __MIN_SUBNORMAL)
end

local function __nextDownPositive(x)
  local m, e = math.frexp(x)
  local step = 2 ^ (e - 53)
  if m == 0.5 then step = step / 2 end
  return x - math.max(step, __MIN_SUBNORMAL)
end

local function __nextUp(x)
  if x ~= x or x == math.huge then return x end
  if x == -math.huge then return -1.7976931348623157e308 end
  if x == 0 then return __MIN_SUBNORMAL end
  if x > 0 then return x + __ulp(x) end
  return -__nextDownPositive(-x)
end

local function __nextDown(x)
  if x ~= x or x == -math.huge then return x end
  if x == math.huge then return 1.7976931348623157e308 end
  if x == 0 then return -__MIN_SUBNORMAL end
  if x > 0 then return __nextDownPositive(x) end
  return x - __ulp(x)
end

local function __nextTowards(x, to)
  if x ~= x or to ~= to then return 0 / 0 end
  if x == to then return to end
  if to > x then return __nextUp(x) end
  return __nextDown(x)
end
