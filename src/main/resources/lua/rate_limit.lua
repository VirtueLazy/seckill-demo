local current = redis.call('incr',KEYS[1])

if current == 1 then
    redis.call('expire',KEYS[1],ARGV[1])
end

if current > tonumber(ARGV[2]) then
    return 0
else
    return 1
end