-- Lists are plain Lua arrays, Sets are {list, has} and Maps are {keys, map}; there's no static
-- element type to tell a nested collection apart from a class instance, so printing one has to
-- look at the table's shape at runtime.

local function __fmt(v)
  if v == nil or v == __NULL then return "null" end
  if type(v) ~= "table" then return tostring(v) end
  local mt = getmetatable(v)
  if mt and mt.__tostring then return tostring(v) end
  local parts = {}
  if v.list ~= nil and v.has ~= nil then
    for _, e in ipairs(v.list) do table.insert(parts, __fmt(e)) end
    return "[" .. table.concat(parts, ", ") .. "]"
  end
  if v.keys ~= nil and v.map ~= nil then
    for _, k in ipairs(v.keys) do table.insert(parts, __fmt(k) .. "=" .. __fmt(v.map[k])) end
    return "{" .. table.concat(parts, ", ") .. "}"
  end
  if v.first ~= nil and v.second ~= nil and #v == 0 then
    return "(" .. __fmt(v.first) .. ", " .. __fmt(v.second) .. ")"
  end
  for _, e in ipairs(v) do table.insert(parts, __fmt(e)) end
  return "[" .. table.concat(parts, ", ") .. "]"
end
