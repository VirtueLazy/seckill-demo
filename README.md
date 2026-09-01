# 电商秒杀系统 Seckill Demo

基于 Spring Boot 4.1.1、Redis、RabbitMQ 和 MySQL 实现的秒杀练习项目，重点解决并发场景中的超卖、重复下单和数据库瞬时压力问题。

## 技术栈

- Spring Boot 4.1.1、JDK 17
- Spring Security、JWT
- Redis、Lua
- RabbitMQ
- MyBatis-Plus、MySQL 8
- JUnit、Mockito

## 秒杀链路

```text
用户请求
  -> JWT 认证
  -> Redis 限流
  -> Lua 原子执行活动校验、用户去重和库存扣减
  -> 发送 RabbitMQ 消息
  -> 消费者事务内扣减数据库库存并创建订单
```

## 核心设计

### 防止超卖

Redis Lua 把库存检查和扣减放在一次原子操作中。消费者落库时使用：

```sql
UPDATE seckill_activity
SET seckill_stock = seckill_stock - 1
WHERE id = ? AND seckill_stock > 0;
```

Redis 负责挡住高并发请求，数据库条件更新作为最后一道库存保护。

### 防止重复下单

- Redis Set 在入口拦截同一用户的重复请求。
- 数据库唯一索引 `(seckill_activity_id, user_id)` 防止重复消息生成两份订单。

### MQ 异步削峰

接口完成 Redis 裁决后发送 RabbitMQ 消息，消费者异步执行数据库事务。消费者在事务提交后手动 ACK；异常消息进入死信队列，避免无限重试。

### 权限与配置

- 普通用户可以参加秒杀和查询商品。
- 库存预热及商品写操作要求 `ADMIN` 角色。
- 数据库、Redis、RabbitMQ 密码及 JWT 密钥均支持环境变量覆盖。

## 本地运行

环境要求：JDK 17、Maven、Docker Compose。

```bash
docker compose up -d
mvn test
mvn spring-boot:run
```

`compose.yaml` 会启动 MySQL、Redis 和 RabbitMQ，`db/schema.sql` 会初始化完整表结构与示例活动。RabbitMQ 管理页面为 `http://localhost:15672`。

生产或共享环境不要使用仓库中的开发默认密码。环境变量示例见 `.env.example`。

## 主要接口

- `POST /api/user/register`：注册
- `POST /api/user/login`：登录并获取 JWT
- `POST /api/seckill/preload/{activityId}`：管理员预热库存
- `POST /api/seckill/{activityId}`：发起秒杀
- `GET /api/product/list`：商品列表

除注册和登录外，请求需要携带 `Authorization: Bearer <token>`。新注册用户默认为 `USER`；将数据库中的角色改为 `ADMIN` 后，需要重新登录获取 Token。

## 测试

```bash
mvn test
```

`SeckillConcurrentTest` 是需要先启动完整环境的并发验证程序。它可以验证 Redis 接受的请求数不会超过库存，但最终还应检查数据库订单数和剩余库存。

## 已知边界

这是一个便于学习和面试讲解的基础版本，不宣称已经达到生产级可靠性：

- Redis 扣库存成功后，如果 MQ 发送失败，目前只会通过发布确认回调记录日志，没有自动补偿。
- 死信消费者目前记录错误后 ACK，没有后台重放功能。
- 现有并发程序属于功能验证，不是正式的高并发性能报告。

这些边界可以作为后续学习方向，但不建议在尚未掌握时直接堆入项目。
