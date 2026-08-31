-- KEYS[1]: seckill:stock:{activityId}        库存 key
-- KEYS[2]: seckill:purchased:{activityId}    已购买用户集合
-- ARGV[1]: userId
-- ARGV[2]: 活动开始时间（epoch 秒）
-- ARGV[3]: 活动结束时间（epoch 秒）
-- ARGV[4]: 当前时间（epoch 秒，由应用传入，避免依赖 Redis 的 os 库）
--
-- 返回码：
--  0  成功
-- -1  库存不足
-- -2  库存未预热
-- -3  重复购买
-- -4  活动未开始
-- -5  活动已结束

local user = redis.call('sismember', KEYS[2], ARGV[1])
if user == 1 then
    return -3
end

if tonumber(ARGV[4]) < tonumber(ARGV[2]) then
    return -4
end

if tonumber(ARGV[4]) > tonumber(ARGV[3]) then
    return -5
end

local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil then
    return -2
end

if stock <= 0 then
    return -1
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])

return 0
