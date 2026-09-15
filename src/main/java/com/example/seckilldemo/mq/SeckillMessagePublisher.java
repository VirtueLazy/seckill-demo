package com.example.seckilldemo.mq;

import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SeckillMessagePublisher {
    private final RabbitTemplate rabbitTemplate;

    public SeckillMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CorrelationData publish(SeckillMessage seckillMessage, boolean compensateOnFailure) {
        Long activityId = seckillMessage.getActivityId();
        Long userId = seckillMessage.getUserId();
        SeckillCorrelationData correlationData =
                new SeckillCorrelationData(activityId, userId, compensateOnFailure);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                seckillMessage,
                message -> {
                    message.getMessageProperties().setHeader(
                            SeckillCorrelationData.HEADER_ACTIVITY_ID, activityId);
                    message.getMessageProperties().setHeader(
                            SeckillCorrelationData.HEADER_USER_ID, userId);
                    message.getMessageProperties().setHeader(
                            SeckillCorrelationData.HEADER_COMPENSATE_ON_FAILURE, compensateOnFailure);
                    return message;
                },
                correlationData);
        return correlationData;
    }
}
