package com.example.seckilldemo.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestRedisController {

    private final StringRedisTemplate stringRedisTemplate;

    public TestRedisController(StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/redis")
    public String testRedis() {
        stringRedisTemplate.opsForValue().set("test:key","hello redis");
        return stringRedisTemplate.opsForValue().get("test:key");
    }
}
