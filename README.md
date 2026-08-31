# 电商秒杀系统 Seckill Demo

基于 **Spring Boot + Redis + RabbitMQ + MySQL** 的高并发秒杀系统，解决了秒杀场景下的**超卖、重复下单、数据库压力过大**三大核心问题。

## 技术栈

- Spring Boot 4 + Spring Security + JWT（无状态认证）
- Redis + Lua 脚本（库存原子扣减、去重、限流）
- RabbitMQ（异步下单削峰）
- MyBatis-Plus + MySQL（最终落账）
- JMeter（并发压测）

## 架构与核心链路

```
用户抢购请求
   │
   ▼
① JWT 认证 ──► ② 接口限流（Redis 计数器，每用户 1 次/秒）
   │
   ▼
③ Redis Lua 脚本原子裁决（单脚本内完成，无人能插队）
   ├─ 用户去重（已购买集合）
   ├─ 活动时间窗口校验（未开始/已结束）
   └─ 库存扣减
   │
   ▼
④ 秒杀成功 → 发送 MQ 消息 → 立即返回"抢购成功"
   │
   ▼
⑤ 消费者 @Transactional 落库
   ├─ 幂等检查（防重复消费）
   ├─ CAS 扣数据库库存（WHERE stock > 0）
   └─ 插入订单（唯一索引兜底，撞了整体回滚）
```

**设计思想**：Redis 挡流量 + 快速裁决，MQ 削峰异步化，MySQL 最终落账 + 兜底一致性。三个角色各司其职。

## 核心亮点

1. **防超卖（多级防御）**
   - Redis Lua 脚本将"去重 + 时间校验 + 扣库存 + 记购买"合并为一次**原子操作**，单线程执行杜绝并发超卖
   - 数据库 CAS 更新（`WHERE stock > 0`）兜底 Redis 与 DB 的最终一致性

2. **防重复下单（双保险）**
   - Redis 已购买集合：正常情况第一道拦截
   - 数据库唯一索引 `uk_activity_user(seckill_activity_id, user_id)`：Redis 失灵（数据丢失/消息重投）时的最终防线，配合 `@Transactional` 整体回滚

3. **高响应（MQ 削峰）**
   - 秒杀接口只操作内存级 Redis + 发消息，用户立即收到结果
   - 数据库写入由消费者异步排队处理，避免瞬间打爆数据库

4. **认证与安全**
   - JWT + Spring Security 无状态认证，接口不信任前端传参，从 token 中取用户身份
   - Redis Lua 计数器限流，防脚本刷单

5. **MQ 可靠投递**
   - **手动 ACK**：用 `TransactionTemplate` 保证"事务提交成功后再确认消息"，避免先确认后回滚导致丢消息
   - **死信队列（DLQ）**：处理失败的消息自动转投死信队列，不无限重试、便于人工排查
   - **发布确认 + 路由回调**：消息未到达 Broker / 路由失败时生产者能感知并告警

## 压测结果（JMeter）

| 指标 | 数值 |
|---|---|
| 并发用户数 | 20 |
| 库存 | 9（全部成交） |
| 超卖 | **0** |
| 平均响应 | 18 ms |
| 最大响应 | 21 ms |

压测脚本见 `src/test/java/com/example/seckilldemo/SeckillConcurrentTest.java`。

## 如何运行

**环境要求**：JDK 17、MySQL 8、Redis、RabbitMQ

1. 建库建表：执行 `db/seckill_order.sql`（含唯一索引），并按实体类建 `product`、`user`、`seckill_activity` 表
2. 修改 `src/main/resources/application.yaml` 中的 MySQL / Redis / RabbitMQ 连接信息
3. 启动 `SeckillDemoApplication`
4. 注册用户获取 JWT token，调用 `POST /api/seckill/preload/{activityId}` 预热库存
5. 调用 `POST /api/seckill/{activityId}` 发起秒杀

> 注意：`seckill.lua` 中的活动时间由应用侧传入（epoch 秒），不依赖 Redis 的 `os` 库，兼容开启 strict Lua 模式的环境。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/user/register | 注册 |
| POST | /api/user/login | 登录，返回 JWT |
| POST | /api/seckill/preload/{id} | 库存预热（需认证） |
| POST | /api/seckill/{id} | 秒杀下单（需认证） |
| GET | /api/product/list | 商品列表（需认证） |

## 项目结构

```
src/main/java/com/example/seckilldemo/
├── config/        # Security / RabbitMQ / Redis 脚本配置
├── controller/    # 接口层
├── service/       # 业务层
├── mq/            # MQ 消费者
├── mapper/        # MyBatis-Plus
├── entity/        # 实体
├── dto/           # 传输对象
├── filter/        # JWT 过滤器
├── util/          # JWT 工具
└── resources/lua/ # 秒杀与限流脚本
```
