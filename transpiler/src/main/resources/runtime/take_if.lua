local function __takeIf(value, predicate, invert)
   if predicate(value) ~= invert then return value end
end
