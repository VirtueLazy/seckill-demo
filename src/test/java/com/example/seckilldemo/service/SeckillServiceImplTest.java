package com.example.seckilldemo.service;

import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.exception.BusinessException;
import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.service.impl.SeckillServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock(name = "seckillScript")
    private RedisScript<Long> seckillScript;
    @Mock(name = "rateLimitScript")
    private RedisScript<Long> rateLimitScript;
    @Mock
    private SeckillReservationCompensator reservationCompensator;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private SeckillServiceImpl service;

    @Test
    void synchronousPublishFailureCompensatesReservationAndReturnsFailure() {
        when(stringRedisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("seckill:limit:2"),
                "1", "1"))
                .thenReturn(1L);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("seckill:window:1"))
                .thenReturn(Map.of("start", "0", "end", String.valueOf(Long.MAX_VALUE)));
        when(stringRedisTemplate.execute(
                eq(seckillScript),
                eq(Arrays.asList("seckill:stock:1", "seckill:purchased:1")),
                eq("2"), eq("0"), eq(String.valueOf(Long.MAX_VALUE)), anyString()))
                .thenReturn(0L);
        doThrow(new AmqpException("connection unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(RabbitMQConfig.SECKILL_EXCHANGE),
                        eq(RabbitMQConfig.SECKILL_ROUTING_KEY),
                        any(SeckillMessage.class),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.doSeckill(1L, 2L));

        assertEquals("秒杀请求暂未受理，请稍后重试", exception.getMessage());
        verify(reservationCompensator).compensate(1L, 2L, "publish-exception");
    }
}
