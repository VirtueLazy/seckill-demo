-- KEYS[1]: seckill:stock:{activityId}        库存 key
-- KEYS[2]: seckill:purchased:{activityId}    已购买用户集合
-- ARGV[1]: userId
--
-- 只有用户仍在已购买集合中时才恢复库存。
-- SREM 和 INCR 在同一个 Lua 脚本中执行，使重复回调不会重复增加库存。

local removed = redis.call('srem', KEYS[2], ARGV[1])
if removed == 0 then
    return 0
end

redis.call('incr', KEYS[1])
return 1
