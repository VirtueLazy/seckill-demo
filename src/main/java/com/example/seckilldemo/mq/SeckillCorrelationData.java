package com.example.seckilldemo.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.UUID;

public class SeckillCorrelationData extends CorrelationData {
    public static final String HEADER_ACTIVITY_ID = "x-seckill-activity-id";
    public static final String HEADER_USER_ID = "x-seckill-user-id";
    public static final String HEADER_COMPENSATE_ON_FAILURE = "x-seckill-compensate-on-failure";

    private final Long activityId;
    private final Long userId;
    private final boolean compensateOnFailure;

    public SeckillCorrelationData(Long activityId, Long userId) {
        this(activityId, userId, true);
    }

    public SeckillCorrelationData(Long activityId, Long userId, boolean compensateOnFailure) {
        super("seckill:" + activityId + ":" + userId + ":" + UUID.randomUUID());
        this.activityId = activityId;
        this.userId = userId;
        this.compensateOnFailure = compensateOnFailure;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isCompensateOnFailure() {
        return compensateOnFailure;
    }
}
