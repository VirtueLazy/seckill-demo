package com.example.seckilldemo.mq;

import com.example.seckilldemo.service.SeckillReservationCompensator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SeckillPublishFailureHandler
        implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private final SeckillReservationCompensator compensator;

    public SeckillPublishFailureHandler(SeckillReservationCompensator compensator) {
        this.compensator = compensator;
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            return;
        }

        if (correlationData instanceof SeckillCorrelationData seckillData) {
            compensateSafely(seckillData.getActivityId(), seckillData.getUserId(),
                    "broker-nack: " + cause);
            return;
        }
        log.error("消息发送到交换机失败但缺少秒杀关联信息，correlationData={}，原因={}", correlationData, cause);
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        Map<String, Object> headers = returned.getMessage().getMessageProperties().getHeaders();
        Long activityId = asLong(headers.get(SeckillCorrelationData.HEADER_ACTIVITY_ID));
        Long userId = asLong(headers.get(SeckillCorrelationData.HEADER_USER_ID));
        if (activityId != null && userId != null) {
            compensateSafely(activityId, userId, "unroutable: " + returned.getReplyText());
        } else {
            log.error("消息路由失败但缺少补偿信息：exchange={}，routingKey={}，replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        }
    }

    private void compensateSafely(Long activityId, Long userId, String reason) {
        try {
            compensator.compensate(activityId, userId, reason);
        } catch (Exception e) {
            log.error("自动补偿失败，需要人工核对：activityId={}，userId={}，reason={}",
                    activityId, userId, reason, e);
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
