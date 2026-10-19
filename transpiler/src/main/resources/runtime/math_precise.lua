-- kotlin.math cbrt / expm1 / ln1p / IEEErem. Lua has no cbrt, expm1 or log1p, so these use the
-- standard correction tricks; results can differ from the JVM in the last digit.

local function __cbrt(x)
  if x ~= x or x == 0 or x == math.huge or x == -math.huge then return x end
  local a = math.abs(x)
  local r = a ^ (1 / 3)
  r = r - (r * r * r - a) / (3 * r * r)
  if x < 0 then return -r end
  return r
end

local function __expm1(x)
  if x ~= x or x == math.huge then return x end
  if x == -math.huge then return -1 end
  local u = math.exp(x)
  if u == 1 then return x end
  local um1 = u - 1
  if um1 == -1 then return -1 end
  return um1 * x / math.log(u)
end

local function __ln1p(x)
  if x ~= x or x == math.huge then return x end
  local u = 1 + x
  if u == 1 then return x end
  return math.log(u) * x / (u - 1)
end

local function __ieeerem(x, y)
  if x ~= x or y ~= y or y == 0 or x == math.huge or x == -math.huge then return 0 / 0 end
  if y == math.huge or y == -math.huge then return x end
  local r = math.fmod(x, y)
  local ay = math.abs(y)
  local ar = math.abs(r)
  if ar * 2 > ay or (ar * 2 == ay and ((math.abs(x) - ar) / ay) % 2 == 1) then
    if r >= 0 then r = r - ay else r = r + ay end
  end
  return r
end
