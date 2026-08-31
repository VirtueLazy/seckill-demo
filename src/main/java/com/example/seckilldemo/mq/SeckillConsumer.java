package com.example.seckilldemo.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.entity.SeckillActivity;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class SeckillConsumer {
    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillMessage(SeckillMessage message) {

        // 幂等检查：同一个人同一活动已有订单，直接跳过（防止重复消费）
        Long existingCount = seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getSeckillActivityId, message.getActivityId())
                        .eq(SeckillOrder::getUserId, message.getUserId()));
        if (existingCount > 0) {
            log.info("订单已存在，跳过重复消息：userId={}", message.getUserId());
            return;
        }

        // CAS 扣库存：只扣还有库存的，返回 0 说明库存没了
        int rows = seckillActivityMapper.deductStock(message.getActivityId());
        if (rows == 0) {
            log.warn("数据库库存不足，消息处理失败：userId={}", message.getUserId());
            return;
        }

        SeckillActivity activity = seckillActivityMapper.selectById(message.getActivityId());

        SeckillOrder order = new SeckillOrder();
        order.setSeckillActivityId(message.getActivityId());
        order.setUserId(message.getUserId());
        order.setProductId(activity.getProductId());
        order.setSeckillPrice(activity.getSeckillPrice());
        // 若这里撞上唯一索引（并发重复），抛 DuplicateKeyException
        // -> @Transactional 会把整个事务（含扣库存）全部回滚，不会出现"扣了库存没订单"
        seckillOrderMapper.insert(order);

        log.info("订单生成成功：userId={}", message.getUserId());
    }
}
