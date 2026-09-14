package com.example.seckilldemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class SeckillReservationCompensator {
    private static final String STOCK_KEY = "seckill:stock:";
    private static final String PURCHASED_KEY = "seckill:purchased:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> compensateSeckillScript;

    public SeckillReservationCompensator(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("compensateSeckillScript") RedisScript<Long> compensateSeckillScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.compensateSeckillScript = compensateSeckillScript;
    }

    /**
     * 撤销尚未成功发布的秒杀预约。Lua 脚本保证重复补偿不会重复增加库存。
     *
     * @return true 表示本次实际恢复了库存；false 表示预约已被其他回调补偿
     */
    public boolean compensate(Long activityId, Long userId, String reason) {
        Long result = stringRedisTemplate.execute(
                compensateSeckillScript,
                Arrays.asList(STOCK_KEY + activityId, PURCHASED_KEY + activityId),
                String.valueOf(userId));
        if (result == null) {
            throw new IllegalStateException("Redis 未返回补偿结果");
        }

        boolean compensated = result == 1L;
        if (compensated) {
            log.warn("秒杀预约已补偿：activityId={}，userId={}，reason={}", activityId, userId, reason);
        } else {
            log.info("秒杀预约无需重复补偿：activityId={}，userId={}，reason={}", activityId, userId, reason);
        }
        return compensated;
    }
}
