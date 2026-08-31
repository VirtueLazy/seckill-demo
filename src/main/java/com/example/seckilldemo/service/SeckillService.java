package com.example.seckilldemo.service;

public interface SeckillService {
    boolean doSeckill(Long activityId,Long userId);

    void preloadStock(Long activityId);
}
