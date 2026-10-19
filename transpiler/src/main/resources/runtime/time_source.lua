-- Time marks and sources. A source only has to say what time it is, in nanoseconds.

local __Mark = {}
__Mark.__index = __Mark
local function __mark(source, at) return setmetatable({source = source, at = at}, __Mark) end
function __Mark:elapsedNow() return __dur(self.source:readNs() - self.at) end
function __Mark:hasPassedNow() return self.source:readNs() >= self.at end
function __Mark:hasNotPassedNow() return self.source:readNs() < self.at end
__Mark.__add = function(a, b) return __mark(a.source, a.at + b.ns) end
__Mark.__sub = function(a, b)
  if getmetatable(b) == __Mark then return __dur(a.at - b.at) end
  return __mark(a.source, a.at - b.ns)
end
__Mark.__eq = function(a, b) return a.at == b.at end
__Mark.__lt = function(a, b) return a.at < b.at end
__Mark.__le = function(a, b) return a.at <= b.at end

local function __markNow(self) return __mark(self, self:readNs()) end

local function __asClock(source, origin)
  local start = source:markNow()
  return {now = function() return origin + start:elapsedNow() end}
end

local __Monotonic = {markNow = __markNow, asClock = __asClock}
function __Monotonic:readNs() return os.clock() * 1e9 end
local __TimeSource = {Monotonic = __Monotonic}

local __TestTimeSource = {markNow = __markNow, asClock = __asClock}
__TestTimeSource.__index = __TestTimeSource
function __TestTimeSource.new() return setmetatable({t = 0}, __TestTimeSource) end
function __TestTimeSource:readNs() return self.t end
function __TestTimeSource:plusAssign(d) self.t = self.t + d.ns end
__TestTimeSource.__add = function(source, d)
  source:plusAssign(d)
  return source
end

local function __sourceClass()
  local class = {markNow = __markNow, asClock = __asClock}
  class.__index = class
  function class.new(unit) return setmetatable({__unit = __NS[unit]}, class) end
  function class:readNs() return self:read() * self.__unit end
  return class
end
AbstractLongTimeSource = __sourceClass()
AbstractDoubleTimeSource = __sourceClass()
