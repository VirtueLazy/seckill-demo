package com.example.seckilldemo.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.entity.SeckillActivity;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.exception.DuplicateOrderException;
import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import com.example.seckilldemo.service.SeckillFailureService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

@Slf4j
@Component
public class SeckillConsumer {
    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final TransactionTemplate transactionTemplate;
    private final SeckillFailureService failureService;

    public SeckillConsumer(SeckillActivityMapper seckillActivityMapper,
                           SeckillOrderMapper seckillOrderMapper,
                           PlatformTransactionManager transactionManager,
                           SeckillFailureService failureService) {
        this.seckillActivityMapper = seckillActivityMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.failureService = failureService;
    }

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillMessage(SeckillMessage message, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 数据库操作放独立事务：事务提交成功后才 ACK，避免"先确认后回滚"导致丢消息
            transactionTemplate.execute(status -> {
                createOrder(message);
                failureService.markResolved(message);
                return null;
            });
            channel.basicAck(deliveryTag, false);
            log.info("订单生成成功：userId={}", message.getUserId());
        } catch (DuplicateOrderException e) {
            // 重复订单是"业务上已成功"：ACK 掉，不重试
            log.warn("重复订单，视为已处理：userId={}，activityId={}", message.getUserId(), message.getActivityId());
            try {
                failureService.markResolved(message);
                channel.basicAck(deliveryTag, false);
            } catch (Exception resolveFailure) {
                log.error("订单已存在但死信记录更新失败，消息重新进入死信队列", resolveFailure);
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception e) {
            // 真正的失败：不重投原队列（避免死循环），转投死信队列人工排查
            log.error("消息处理失败，进入死信队列：userId={}，activityId={}", message.getUserId(), message.getActivityId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void createOrder(SeckillMessage message) {
        // 快路径：先查订单是否存在，避免大部分重复消息走异常分支
        Long existingCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getSeckillActivityId, message.getActivityId())
                        .eq(SeckillOrder::getUserId, message.getUserId()));
        if (existingCount > 0) {
            throw new DuplicateOrderException("订单已存在");
        }

        // CAS 扣库存：只扣还有库存的
        int rows = seckillActivityMapper.deductStock(message.getActivityId());
        if (rows == 0) {
            // Redis 与 DB 库存不一致才会走到这里，属于异常情况，进死信告警
            throw new IllegalStateException("数据库库存不足");
        }

        SeckillActivity activity = seckillActivityMapper.selectById(message.getActivityId());

        SeckillOrder order = new SeckillOrder();
        order.setSeckillActivityId(message.getActivityId());
        order.setUserId(message.getUserId());
        order.setProductId(activity.getProductId());
        order.setSeckillPrice(activity.getSeckillPrice());
        try {
            seckillOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：两个重复消息并发时，后到者插入失败
            throw new DuplicateOrderException("唯一索引拦截到重复订单");
        }
    }

    // 死信先持久化再 ACK，避免原实现记录日志后直接丢失消息。
    @RabbitListener(queues = RabbitMQConfig.SECKILL_DLQ)
    public void handleDeadLetter(SeckillMessage message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            failureService.recordDeadLetter(message, "消费者处理失败，消息被拒绝进入死信队列");
            channel.basicAck(deliveryTag, false);
            log.error("死信消息已持久化：userId={}，activityId={}",
                    message.getUserId(), message.getActivityId());
        } catch (Exception persistFailure) {
            log.error("死信持久化失败，消息保留在队列等待重试：userId={}，activityId={}",
                    message.getUserId(), message.getActivityId(), persistFailure);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
