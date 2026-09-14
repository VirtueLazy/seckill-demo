package com.example.seckilldemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillReservationCompensatorTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<Long> compensateScript;

    @Test
    void compensatesReservationOnlyOnce() {
        SeckillReservationCompensator compensator =
                new SeckillReservationCompensator(redisTemplate, compensateScript);
        when(redisTemplate.execute(
                compensateScript,
                Arrays.asList("seckill:stock:10", "seckill:purchased:10"),
                "20"))
                .thenReturn(1L)
                .thenReturn(0L);

        assertTrue(compensator.compensate(10L, 20L, "test"));
        assertFalse(compensator.compensate(10L, 20L, "duplicate-callback"));

        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                compensateScript,
                Arrays.asList("seckill:stock:10", "seckill:purchased:10"),
                "20");
    }
}
