package com.example.seckilldemo.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.UUID;

public class SeckillCorrelationData extends CorrelationData {
    public static final String HEADER_ACTIVITY_ID = "x-seckill-activity-id";
    public static final String HEADER_USER_ID = "x-seckill-user-id";

    private final Long activityId;
    private final Long userId;

    public SeckillCorrelationData(Long activityId, Long userId) {
        super("seckill:" + activityId + ":" + userId + ":" + UUID.randomUUID());
        this.activityId = activityId;
        this.userId = userId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Long getUserId() {
        return userId;
    }
}
