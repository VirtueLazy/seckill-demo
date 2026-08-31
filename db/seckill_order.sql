-- 秒杀订单表 DDL（含幂等关键约束：唯一索引）
-- 唯一索引 uk_activity_user 是防止重复下单的最终防线：
-- Redis 的 Lua 原子去重是第一道防线，这里兜底，
-- 即使消息重试/Redis 数据丢失，数据库也会拒绝重复订单。

CREATE TABLE IF NOT EXISTS `seckill_order` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seckill_activity_id` BIGINT      NOT NULL COMMENT '秒杀活动ID',
  `user_id`            BIGINT       NOT NULL COMMENT '用户ID',
  `product_id`         BIGINT       NOT NULL COMMENT '商品ID',
  `seckill_price`      DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
  `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`seckill_activity_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='秒杀订单表';

-- 如果你已经建了表，执行下面这句即可：
-- ALTER TABLE seckill_order ADD UNIQUE KEY uk_activity_user (seckill_activity_id, user_id);
