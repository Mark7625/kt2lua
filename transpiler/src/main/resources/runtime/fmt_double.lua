-- Lua 5.1-style numbers have no int/float split, so a Double has to be formatted from its static
-- type: Kotlin prints the shortest digits that round-trip ("3.141592653589793", "5.0", "1.0E-4"),
-- switching to scientific notation below 1e-3 and from 1e7 up.

local function __fmtDouble(x)
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
