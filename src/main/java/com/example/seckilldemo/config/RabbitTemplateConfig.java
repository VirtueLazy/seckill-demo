package com.example.seckilldemo.config;

import com.example.seckilldemo.mq.SeckillPublishFailureHandler;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTemplateConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter,
                                         SeckillPublishFailureHandler publishFailureHandler) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 关键：自定义 RabbitTemplate 后，必须手动设置消息转换器，
        // 否则会用默认的 Java 序列化（application/x-java-serialized-object），消费者解析不了
        rabbitTemplate.setMessageConverter(messageConverter);
        // mandatory=true：消息路由不到队列时触发 ReturnsCallback，而不是被静默丢弃
        rabbitTemplate.setMandatory(true);

        // Broker NACK 或消息无法路由时，通过幂等 Lua 脚本恢复 Redis 预约库存。
        rabbitTemplate.setConfirmCallback(publishFailureHandler);
        rabbitTemplate.setReturnsCallback(publishFailureHandler);
        return rabbitTemplate;
    }
}
