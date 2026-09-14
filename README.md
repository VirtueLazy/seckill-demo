# 秒杀订单可靠性系统 Seckill Demo

[![CI](https://github.com/VirtueLazy/seckill-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/VirtueLazy/seckill-demo/actions/workflows/ci.yml)

基于 Spring Boot 4.1.1、Redis、RabbitMQ 和 MySQL 实现的秒杀可靠性练习项目，重点验证并发场景中的超卖、重复下单、异步落库和发布失败补偿。项目保留已知边界，不宣称达到生产级高可用。

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
  -> Lua 原子执行活动校验、用户去重和库存预扣
  -> 携带关联信息发送 RabbitMQ 消息
     -> Broker NACK / 无法路由：幂等 Lua 恢复库存与购买资格
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

### 发布失败补偿

- 每条消息携带活动 ID、用户 ID 和唯一关联 ID。
- Broker 明确 NACK，或消息到达交换机却无法路由时，发布回调触发补偿。
- `compensate_seckill.lua` 先从已购买集合移除用户，只有移除成功才恢复库存；因此 Confirm 和 Return 重复触发也不会重复加库存。
- `convertAndSend` 同步抛出异常时同样尝试撤销预约，并向调用方返回失败。

这套补偿处理的是“明确失败”。如果 Broker 实际收到了消息，但应用在收到确认前发生网络中断，结果仍具有歧义，生产系统需要预约状态、持久化事件或定时对账进一步兜底。

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

- `SeckillReservationCompensatorTest`：验证重复补偿只恢复一次预约。
- `SeckillPublishFailureHandlerTest`：验证 Broker NACK、正常 ACK 和无法路由三种发布结果。
- `SeckillServiceImplTest`：验证同步发布异常会撤销预约，并且不会向调用方返回成功。
- `SeckillConcurrentTest`：需要先启动完整环境的并发验证程序；它验证 Redis 接受数不超过库存，但还不是正式性能报告。

GitHub Actions 会启动 MySQL、Redis、RabbitMQ 并执行 Maven 测试。仓库中的通过状态是可复现的功能验证，不代表线上容量。

## 已知边界

这是一个便于学习和面试讲解的基础版本，不宣称已经达到生产级可靠性：

- 网络中断可能产生“Broker 已接收但应用未收到确认”的歧义结果，目前依赖日志人工对账。
- 如果发布回调执行补偿时 Redis 不可用，目前只记录错误，没有持久化补偿任务。
- 死信消费者目前记录错误后 ACK，没有后台重放功能。
- 现有并发程序属于功能验证，不是正式的高并发性能报告。

这些边界可以作为后续学习方向，但不建议在尚未掌握时直接堆入项目。
