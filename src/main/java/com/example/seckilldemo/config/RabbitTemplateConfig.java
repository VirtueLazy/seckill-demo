package com.example.seckilldemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitTemplateConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 关键：自定义 RabbitTemplate 后，必须手动设置消息转换器，
        // 否则会用默认的 Java 序列化（application/x-java-serialized-object），消费者解析不了
        rabbitTemplate.setMessageConverter(messageConverter);
        // mandatory=true：消息路由不到队列时触发 ReturnsCallback，而不是被静默丢弃
        rabbitTemplate.setMandatory(true);

        // 发布确认：ack=true 说明 Broker 已收到，ack=false 说明投递失败（需补偿）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到交换机失败，id={}，原因：{}", correlationData, cause);
            }
        });

        // 路由失败回调：消息到了交换机但没匹配到任何队列（如路由键写错）
        rabbitTemplate.setReturnsCallback(returned ->
                log.error("消息路由失败：exchange={}，routingKey={}，replyText={}，body={}",
                        returned.getExchange(), returned.getRoutingKey(),
                        returned.getReplyText(), new String(returned.getMessage().getBody())));
        return rabbitTemplate;
    }
}
