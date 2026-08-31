package com.example.seckilldemo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // 死信相关：处理失败的消息进死信队列，供人工排查
    public static final String SECKILL_DLQ = "seckill.order.dlq";
    public static final String SECKILL_DLX = "seckill.order.dlx";
    public static final String SECKILL_DLQ_ROUTING_KEY = "seckill.order.dlq";

    @Bean
    public Queue seckillQueue() {
        // x-dead-letter-*：消息被 nack/过期时，转投到指定的死信交换机+路由键
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", SECKILL_DLX);
        args.put("x-dead-letter-routing-key", SECKILL_DLQ_ROUTING_KEY);
        return new Queue(SECKILL_QUEUE, true, false, false, args);
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public Queue seckillDlq() {
        return new Queue(SECKILL_DLQ, true);
    }

    @Bean
    public DirectExchange seckillDlx() {
        return new DirectExchange(SECKILL_DLX);
    }

    @Bean
    public Binding seckillDlqBinding() {
        return BindingBuilder.bind(seckillDlq()).to(seckillDlx()).with(SECKILL_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
