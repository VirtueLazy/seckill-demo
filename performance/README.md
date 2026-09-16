# 秒杀压测说明

压测只针对本地或隔离测试环境。`reset.sql` 会删除活动 1 的订单和失败消息，禁止对生产或共享数据执行。

## 1. 启动服务

```bash
docker compose up -d --wait
mvn spring-boot:run
```

## 2. 重置一轮测试

默认场景使用活动 1、库存 50。先重置 MySQL：

```powershell
Get-Content -Raw performance/reset.sql | docker compose exec -T mysql mysql -useckill -pseckill_dev_password seckill_demo
```

再重置并预热 Redis。这里直接写入测试夹具，避免把管理员登录流程计入压测：

```bash
docker compose exec redis redis-cli DEL seckill:stock:1 seckill:window:1 seckill:purchased:1
docker compose exec redis redis-cli SET seckill:stock:1 50
docker compose exec redis redis-cli HSET seckill:window:1 start 0 end 4102444799
```

修改库存时，必须同时修改 `reset.sql` 中的 `@stock`、Redis 库存和运行命令的 `STOCK`。

## 3. 并发正确性测试

这个测试为每个请求创建独立用户，避免所有请求被单用户限流拦截：

```bash
mvn -Dtest=SeckillConcurrentTest -Dseckill.load.enabled=true -Dseckill.users=20 -Dseckill.stock=50 test
```

它输出受理、售罄、异常分类以及 P50、P95、P99，但定位是正确性验证，不是容量结论。

## 4. k6 负载测试

安装 k6 后运行：

```bash
k6 run --summary-export performance/results/latest-summary.json -e USERS=100 -e STOCK=50 -e ACTIVITY_ID=1 performance/seckill.js
```

建议每档执行前重新运行第 2 步，预热一轮后再连续测试三次：

- 20 用户 / 50 库存：冒烟验证
- 100 用户 / 50 库存：基础负载
- 500 用户 / 100 库存：压力测试
- 1000 用户 / 100 库存：观察错误率和延迟拐点

k6 只统计秒杀请求的 `seckill_duration`；注册和登录发生在 `setup()`，不会混入秒杀延迟。结果写入 `performance/results/latest-summary.json`，该目录不会提交到 Git。

## 5. 等待异步消费并核验

RabbitMQ 队列清空后执行：

```powershell
Get-Content -Raw performance/verify.sql | docker compose exec -T mysql mysql -useckill -pseckill_dev_password seckill_demo
docker compose exec redis redis-cli GET seckill:stock:1
docker compose exec redis redis-cli SCARD seckill:purchased:1
```

必须同时满足：

- 接口受理数不超过初始库存；
- 最终订单数等于接口受理数；
- 重复订单查询返回空集；
- Redis 与数据库剩余库存一致；
- `PENDING` 死信数量为 0。

报告中记录机器配置、用户数、库存、吞吐量、P95/P99、订单数和异常分类。单机压测结果只代表该环境，不外推为生产容量。
