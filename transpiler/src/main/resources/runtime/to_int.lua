local function __toInt(x)
  x = tonumber(x)
  if x >= 0 then return math.floor(x) else return math.ceil(x) end
end
