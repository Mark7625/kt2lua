local function __copyRecord(source, changes)
   local result = {}
   for key, value in pairs(source) do result[key] = value end
   for _, change in ipairs(changes) do result[change.key] = change.value end
   return result
end
