-- Kotlin's % truncates toward zero (result has the dividend's sign); Lua's % floors (result has
-- the divisor's sign). They only disagree when the remainder is nonzero and the signs of a and
-- the floor-based remainder differ, in which case subtracting b converts floor-mod to trunc-mod.

local function __mod(a, b)
  local r = a % b
  if r ~= 0 and (r < 0) ~= (a < 0) then
    r = r - b
  end
  return r
end
