local function __runCatching(block)
   local ok, value = pcall(block)
   local result = {isSuccess = ok, isFailure = not ok}
   if ok then result.value = value else result.error = value end
   return result
end
local function __resultOnSuccess(result, action)
   if result.isSuccess then action(result.value) end
   return result
end
local function __resultOnFailure(result, action)
   if result.isFailure then action(result.error) end
   return result
end
local function __resultGetOrNull(result)
   if result.isSuccess then return result.value end
end
