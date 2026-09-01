package com.example.seckilldemo.service.impl;

import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.entity.SeckillActivity;
import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.service.SeckillService;
import com.example.seckilldemo.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String WINDOW_KEY = "seckill:window:";
    private static final String PURCHASED_KEY = "seckill:purchased:";
    private static final String LIMIT_KEY = "seckill:limit:";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisScript<Long> seckillScript;

    @Autowired
    private RedisScript<Long> rateLimitScript;

    @Override
    public void preloadStock(Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("秒杀活动不存在");
        }

        // 库存预热到 Redis，秒杀请求不再打 DB
        stringRedisTemplate.opsForValue().set(STOCK_KEY + activityId, String.valueOf(activity.getSeckillStock()));

        // 活动时间窗口也缓存，秒杀时由 Lua 原子判断开始/结束
        long startEpoch = activity.getStartTime() != null
                ? activity.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond() : 0L;
        long endEpoch = activity.getEndTime() != null
                ? activity.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond() : Long.MAX_VALUE;
        Map<String, String> window = new HashMap<>();
        window.put("start", String.valueOf(startEpoch));
        window.put("end", String.valueOf(endEpoch));
        stringRedisTemplate.opsForHash().putAll(WINDOW_KEY + activityId, window);

        log.info("库存预热完成：activityId={}，库存={}，start={}，end={}",
                activityId, activity.getSeckillStock(), startEpoch, endEpoch);
    }

    @Override
    public boolean doSeckill(Long activityId, Long userId) {
        // 接口限流：每个用户 1 秒 1 次，防止脚本刷接口
        String limitKey = LIMIT_KEY + userId;
        Long allowed = stringRedisTemplate.execute(rateLimitScript, Collections.singletonList(limitKey), "1", "1");
        if (allowed == null || allowed == 0) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }

        String stockKey = STOCK_KEY + activityId;
        String purchasedSetKey = PURCHASED_KEY + activityId;

        // 读取预热时缓存的时间窗口，传给 Lua 原子校验
        Map<Object, Object> window = stringRedisTemplate.opsForHash().entries(WINDOW_KEY + activityId);
        String startEpoch = (String) window.get("start");
        String endEpoch = (String) window.get("end");
        if (startEpoch == null || endEpoch == null) {
            throw new BusinessException("秒杀活动未预热");
        }

        // 当前时间由应用传入，避免依赖 Redis 的 os 库（部分环境 strict Lua 禁用它）
        long nowEpoch = System.currentTimeMillis() / 1000;
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, purchasedSetKey),
                String.valueOf(userId), startEpoch, endEpoch, String.valueOf(nowEpoch));

        if (result == null) {
            throw new IllegalStateException("秒杀执行异常");
        }
        switch (result.intValue()) {
            case -5 -> throw new BusinessException("秒杀活动已结束");
            case -4 -> throw new BusinessException("秒杀活动尚未开始");
            case -3 -> throw new BusinessException("您已经参与过本次秒杀，请勿重复购买");
            case -2 -> throw new BusinessException("秒杀尚未开始（库存未预热）");
            case -1 -> throw new BusinessException("库存不足，秒杀失败");
        }

        // 购买记录集合设 7 天过期，防止 Redis 内存无限增长
        stringRedisTemplate.expire(purchasedSetKey, 7, TimeUnit.DAYS);

        // 接口只完成 Redis 裁决和消息发送，订单由消费者异步落库。
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                new SeckillMessage(activityId, userId));
        return true;
    }
}
