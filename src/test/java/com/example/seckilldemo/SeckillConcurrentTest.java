package com.example.seckilldemo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀并发压测脚本（main 方式运行，需先启动服务并满足前置条件）：
 *   1. 数据库 seckill_activity 表存在 id=ACTIVITY_ID 的活动，库存 = EXPECTED_STOCK
 *   2. 调用过 POST /api/seckill/preload/{ACTIVITY_ID} 完成库存预热
 *   3. MySQL / Redis / RabbitMQ 正常运行
 * 运行后会：注册+登录拿 token -> 并发秒杀 -> 输出分类统计 -> 断言未超卖
 */
public class SeckillConcurrentTest {
    private static final String BASE = "http://localhost:8080";
    private static final int USER_COUNT = 20;
    private static final int ACTIVITY_ID = 1;
    private static final int EXPECTED_STOCK = 10;

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        // 1. 注册 + 登录，为每个用户获取 JWT
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            String username = "test" + (i + 1);
            String password = "123456";
            register(client, mapper, username, password);
            String token = login(client, mapper, username, password);
            tokens.add(token);
        }
        System.out.println("===== 已为 " + USER_COUNT + " 个用户获取 token =====");

        // 2. 所有线程在同一时刻发起秒杀请求
        CountDownLatch ready = new CountDownLatch(USER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USER_COUNT);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger repeat = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        long begin = System.currentTimeMillis();
        for (String token : tokens) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    ready.await();
                    start.await();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE + "/api/seckill/" + ACTIVITY_ID))
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonNode body = mapper.readTree(response.body());
                    // 成功响应是裸 true，失败响应是 {code:400, message:"..."}。
                    String message = body.path("message").asText();
                    if (body.isBoolean() && body.asBoolean()) {
                        success.incrementAndGet();
                    } else if (message.contains("重复")) {
                        repeat.incrementAndGet();
                    } else if (message.contains("库存不足")) {
                        soldOut.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("请求异常: " + e.getMessage());
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        done.await();
        long cost = System.currentTimeMillis() - begin;

        System.out.println("===== 压测结果 =====");
        System.out.println("并发用户数: " + USER_COUNT);
        System.out.println("总耗时: " + cost + " ms");
        System.out.println("抢购请求成功: " + success.get());
        System.out.println("重复购买拦截: " + repeat.get());
        System.out.println("库存不足: " + soldOut.get());
        System.out.println("其他: " + other.get());

        // 断言 1：成功数不能超过库存（防超卖）
        if (success.get() > EXPECTED_STOCK) {
            System.out.println("FAIL: 成功数(" + success.get() + ")超过库存(" + EXPECTED_STOCK + ")，存在超卖！");
            System.exit(1);
        }
        // 断言 2：请求总数 == 用户数
        if (success.get() + repeat.get() + soldOut.get() + other.get() != USER_COUNT) {
            System.out.println("FAIL: 计数不一致，总请求数与用户数不符！");
            System.exit(1);
        }
        System.out.println("PASS: 成功数 " + success.get() + " <= 库存 " + EXPECTED_STOCK
                + "。请等待异步消费后确认订单数 == " + success.get());
    }

    private static void register(HttpClient client, ObjectMapper mapper, String username, String password) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        node.put("nickname", username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/user/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(node)))
                .build();
        // 用户已存在会返回 400，这里忽略，反正下一步要登录
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String login(HttpClient client, ObjectMapper mapper, String username, String password) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/user/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(node)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = mapper.readTree(response.body());
        // 登录成功返回裸 token 字符串
        if (body.isTextual()) {
            return body.asText();
        }
        return body.path("data").asText();
    }
}
