-- Lua's ipairs()/# treat a raw `nil` as "end of array", so a List<T?> containing Kotlin nulls
-- can't be backed by a plain Lua array with real nil elements - `{1, nil, 3}` silently looks like
-- a 1-element array to ipairs(). __NULL is a sentinel stored in place of nil; __nilEncode wraps a
-- value going into list storage, __nilDecode unwraps one coming back out.

local __NULL = {}
local function __nilEncode(v) if v == nil then return __NULL else return v end end
local function __nilDecode(v) if v == __NULL then return nil else return v end end
