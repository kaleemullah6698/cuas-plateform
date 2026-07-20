-- Token bucket for ARCHITECTURE.md section 7.8, evaluated inside Redis.
--
-- The gateway is horizontally scaled and stateless, so refill-and-consume must be one atomic
-- step across all instances. Done in application code as read-modify-write, three instances would
-- each grant the full quota and the limit would be three times what section 7.8 specifies.
--
-- KEYS[1] bucket key
-- ARGV[1] capacity in tokens (the "burst" column)
-- ARGV[2] refill rate in tokens per second (the "limit / window" column)
-- ARGV[3] current time in epoch milliseconds, supplied by the caller so that every gateway
--         instance and this script agree on one clock rather than mixing two
-- ARGV[4] key time-to-live in milliseconds
--
-- Returns { allowed, whole tokens remaining, milliseconds until the next token }

local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])
local ttl      = tonumber(ARGV[4])

local state  = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(state[1])
local last   = tonumber(state[2])

if tokens == nil or last == nil then
  -- An unseen identity starts with a full bucket; the burst column is what it may spend at once.
  tokens = capacity
  last = now
end

-- Guard against a clock that has stepped backwards: never refill for negative elapsed time.
local elapsed = math.max(0, now - last)
tokens = math.min(capacity, tokens + (elapsed / 1000.0) * rate)

local allowed = 0
if tokens >= 1.0 then
  allowed = 1
  tokens = tokens - 1.0
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'timestamp', now)
redis.call('PEXPIRE', KEYS[1], ttl)

-- Time until the bucket holds a whole token again. Zero when one is already available.
local wait = 0
local deficit = 1.0 - tokens
if deficit > 0 then
  wait = math.ceil(deficit / rate * 1000.0)
end

return { allowed, math.floor(tokens), wait }
