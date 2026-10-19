-- A thrown plain message (`throw Exception("boom")` compiles to error("boom")) reaches `catch`
-- as a bare string; wrapping it gives `e.message` something to read. A thrown class instance
-- (a table) is passed through untouched.

local function __exception(err)
  if type(err) == "table" then return err end
  return setmetatable({message = err}, {__tostring = function(self) return tostring(self.message) end})
end
