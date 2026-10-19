-- kotlin.random.Random. Random(seed) is a port of Kotlin's own XorWow generator on top of 32-bit
-- arithmetic done with doubles (Lua 5.1 has no integer type or bit operators), so every seeded
-- Random is independent and produces exactly the sequence the JVM would. The unseeded
-- Random / Random.Default just wraps math.random. nextLong / nextULong draw from a 53-bit range
-- (Lua numbers are doubles), so a seeded sequence of those is not the JVM's.

local __Random = {}
__Random.__index = __Random
local __P32 = 4294967296

local function __xor32(a, b)
  local r, bit = 0, 1
  for _ = 1, 32 do
    local ab = a % 2
    local bb = b % 2
    if ab ~= bb then r = r + bit end
    a = (a - ab) / 2
    b = (b - bb) / 2
    bit = bit * 2
  end
  return r
end

function __Random.new(seed)
  local s1 = seed % __P32
  local s2 = 0
  if seed < 0 then s2 = __P32 - 1 end
  local self = setmetatable({}, __Random)
  self.x = s1
  self.y = s2
  self.z = 0
  self.w = 0
  self.v = __P32 - 1 - s1
  self.addend = __xor32((s1 * 1024) % __P32, math.floor(s2 / 16))
  for _ = 1, 64 do self:_next() end
  return self
end

function __Random:_next()
  local t = self.x
  t = __xor32(t, math.floor(t / 4))
  self.x = self.y
  self.y = self.z
  self.z = self.w
  local v0 = self.v
  self.w = v0
  t = __xor32(__xor32(t, (t * 2) % __P32), __xor32(v0, (v0 * 16) % __P32))
  self.v = t
  self.addend = (self.addend + 362437) % __P32
  return (t + self.addend) % __P32
end

function __Random:_bits(n)
  if n == 0 then return 0 end
  return math.floor(self:_next() / 2 ^ (32 - n))
end

function __Random:nextInt(a, b)
  local from, upto
  if b ~= nil then
    from, upto = a, b
  elseif a ~= nil then
    from, upto = 0, a
  end
  if from == nil then
    if self.default then return math.random(0, 65535) * 65536 + math.random(0, 65535) - 2147483648 end
    local u = self:_next()
    if u >= 2147483648 then return u - __P32 end
    return u
  end
  if self.default then return math.random(from, upto - 1) end
  local n = upto - from
  if n > 0 and n <= 2147483647 then
    local bitCount, p = 0, 1
    while p < n do
      p = p * 2
      bitCount = bitCount + 1
    end
    local r
    if p == n then
      r = self:_bits(bitCount)
    else
      local bits, v
      repeat
        bits = math.floor(self:_next() / 2)
        v = bits % n
      until bits - v + (n - 1) < 2147483648
      r = v
    end
    return r + from
  end
  while true do
    local r = self:nextInt()
    if r >= from and r < upto then return r end
  end
end

function __Random:nextDouble(a, b)
  local base
  if self.default then
    base = math.random()
  else
    local hi = self:_bits(26)
    local lo = self:_bits(27)
    base = (hi * 134217728 + lo) / 9007199254740992
  end
  if a == nil then return base end
  local from, upto = 0, a
  if b ~= nil then from, upto = a, b end
  return from + base * (upto - from)
end

function __Random:nextBoolean()
  if self.default then return math.random() < 0.5 end
  return self:_bits(1) ~= 0
end

function __Random:nextUInt(a, b)
  if a == nil then
    if self.default then return math.random(0, 65535) * 65536 + math.random(0, 65535) end
    return self:_next()
  end
  if b == nil then a, b = 0, a end
  return self:nextInt(a, b)
end

function __Random:nextLong(a, b)
  if a == nil then return math.floor(self:nextDouble() * 9007199254740992) - 4503599627370496 end
  if b == nil then a, b = 0, a end
  return math.floor(self:nextDouble() * (b - a)) + a
end

function __Random:nextULong(a, b)
  if a == nil then return math.floor(self:nextDouble() * 9007199254740992) end
  return self:nextLong(a, b)
end

function __Random:nextUBytes(n)
  local bytes = {}
  if self.default then
    for i = 1, n do bytes[i] = math.random(0, 255) end
    return bytes
  end
  local position = 0
  for _ = 1, math.floor(n / 4) do
    local v = self:_next()
    for k = 0, 3 do bytes[position + k + 1] = math.floor(v / 2 ^ (8 * k)) % 256 end
    position = position + 4
  end
  local remainder = n - position
  if remainder > 0 then
    local vr = self:_bits(remainder * 8)
    for k = 0, remainder - 1 do bytes[position + k + 1] = math.floor(vr / 2 ^ (8 * k)) % 256 end
  end
  return bytes
end

__Random.Default = setmetatable({default = true}, __Random)
