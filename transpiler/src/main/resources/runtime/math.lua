-- kotlin.math helpers that have no single Lua equivalent. round() ties go to the even neighbour
-- like Kotlin's, sign()/truncate() keep -0.0 and NaN, log10 falls back for runtimes without it.

local function __round(x)
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
