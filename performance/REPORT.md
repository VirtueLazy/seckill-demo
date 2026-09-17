# 秒杀并发验证报告

## 结论

在 GitHub Actions 隔离环境中执行“20 个独立用户同时抢购 10 件库存”的并发正确性冒烟，接口受理 10 个请求、售罄 10 个请求，最终生成 10 笔订单；未发现超卖、重复订单、双端库存差异或待处理死信。

这是一轮可复现的正确性验证，不是正式容量测试，也不代表生产环境 QPS。

## 可追溯信息

- 执行时间：2026-09-16
- 被测提交：[`9a44814`](https://github.com/VirtueLazy/seckill-demo/commit/9a448146a9344c2c9041de742a5c20f863eecb10)
- 原始流水线：[`CI #35043059885`](https://github.com/VirtueLazy/seckill-demo/actions/runs/35043059885)
- 运行环境：GitHub 托管的 `ubuntu-latest` Runner、Temurin JDK 17
- 依赖环境：MySQL 8.4、Redis 7.4、RabbitMQ 4.1
- 测试入口：`SeckillConcurrentTest`

## 场景与口径

- 活动 ID：1
- 初始库存：10
- 并发用户：20，每个请求使用独立账号和 JWT
- 请求模型：所有工作线程就绪后由同一个闸门同时放行，每个用户请求一次秒杀接口
- 计时范围：从并发闸门放行到全部 HTTP 响应结束
- 账号注册和登录发生在计时前，不计入秒杀接口延迟
- 订单通过 RabbitMQ 异步消费生成，HTTP 结束后继续轮询数据库，最长等待 30 秒

## 原始结果

```text
并发用户/总耗时: 20/216 ms
吞吐量: 92.59 req/s
P50/P95/P99: 193/213/215 ms
受理/重复/售罄/其他: 10/0/10/0

orders=10
duplicates=0
db_stock=0
redis_stock=0
pending_dlq=0
```

## 自动判定条件

- 接口受理数不得超过初始库存；
- 最终订单数必须等于初始库存 10；
- 同一活动和用户不得出现重复订单；
- Redis 与 MySQL 剩余库存必须同时为 0；
- `PENDING` 状态的死信数量必须为 0。

任一条件不满足，GitHub Actions 直接失败。

## 限制

- 当前数据只有一轮 20 并发冒烟，样本量不足以形成容量结论；
- GitHub 共享 Runner 的硬件和邻居负载不固定，延迟数字只用于本次运行留档；
- HTTP 成功表示请求已被 Redis 接受并完成消息发布，不等同于订单同步落库；
- 当前没有持续时间型稳态负载、阶梯加压和资源利用率采样；
- 更大并发应使用 [`performance/seckill.js`](seckill.js) 分档执行，并保存每轮 k6 原始结果。

复现步骤和核验 SQL 见 [`performance/README.md`](README.md)。
