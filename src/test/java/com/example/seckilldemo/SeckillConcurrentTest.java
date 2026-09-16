package com.example.seckilldemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 秒杀并发正确性测试。默认不随 mvn test 访问外部服务；显式设置
 * -Dseckill.load.enabled=true 后才运行。
 */
public class SeckillConcurrentTest {
    private static final String BASE = System.getProperty("seckill.baseUrl", "http://localhost:8080");
    private static final int USER_COUNT = Integer.getInteger("seckill.users", 20);
    private static final long ACTIVITY_ID = Long.getLong("seckill.activityId", 1L);
    private static final int EXPECTED_STOCK = Integer.getInteger("seckill.stock", 10);
    private static final String PASSWORD = System.getProperty("seckill.password", "123456");

    @Test
    @EnabledIfSystemProperty(named = "seckill.load.enabled", matches = "true")
    void runAsMavenTest() throws Exception {
        runScenario();
    }

    @Test
    void percentileUsesNearestRank() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 50L);

        assertEquals(30L, percentile(values, 50));
        assertEquals(50L, percentile(values, 95));
        assertEquals(0L, percentile(List.of(), 95));
    }

    @Test
    void extractsTokenFromJsonOrPlainText() {
        ObjectMapper mapper = new ObjectMapper();

        assertEquals("token-a", extractToken(mapper, "\"token-a\""));
        assertEquals("token-b", extractToken(mapper, "token-b"));
        assertEquals("token-c", extractToken(mapper, "{\"data\":\"token-c\"}"));
    }

    public static void main(String[] args) throws Exception {
        runScenario();
    }

    private static void runScenario() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String runId = Long.toString(System.currentTimeMillis(), 36);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            String username = "load_" + runId + "_" + (i + 1);
            register(client, mapper, username, PASSWORD);
            tokens.add(login(client, mapper, username, PASSWORD));
        }
        System.out.println("===== 已为 " + USER_COUNT + " 个独立用户获取 token =====");

        CountDownLatch ready = new CountDownLatch(USER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USER_COUNT);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger repeat = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (String token : tokens) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    long requestStart = System.nanoTime();
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(BASE + "/api/seckill/" + ACTIVITY_ID))
                                    .header("Authorization", "Bearer " + token)
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
                    latencies.add((System.nanoTime() - requestStart) / 1_000_000);
                    classify(mapper, response.body(), accepted, repeat, soldOut, other);
                } catch (Exception e) {
                    System.out.println("请求异常: " + e.getMessage());
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        // 所有工作线程就绪后再同时放行；原脚本缺少 start.countDown()，会永久等待。
        ready.await();
        long begin = System.nanoTime();
        start.countDown();
        done.await();
        long costMillis = (System.nanoTime() - begin) / 1_000_000;

        List<Long> sortedLatencies = latencies.stream().sorted(Comparator.naturalOrder()).toList();
        double throughput = costMillis == 0 ? 0 : USER_COUNT * 1000.0 / costMillis;

        System.out.println("===== 并发正确性测试结果 =====");
        System.out.println("目标地址: " + BASE);
        System.out.println("活动/初始库存: " + ACTIVITY_ID + "/" + EXPECTED_STOCK);
        System.out.println("并发用户/总耗时: " + USER_COUNT + "/" + costMillis + " ms");
        System.out.printf("吞吐量: %.2f req/s%n", throughput);
        System.out.println("P50/P95/P99: " + percentile(sortedLatencies, 50)
                + "/" + percentile(sortedLatencies, 95)
                + "/" + percentile(sortedLatencies, 99) + " ms");
        System.out.println("受理/重复/售罄/其他: " + accepted + "/" + repeat + "/" + soldOut + "/" + other);

        if (accepted.get() > EXPECTED_STOCK) {
            throw new AssertionError("受理数 " + accepted + " 超过库存 " + EXPECTED_STOCK + "，存在超卖");
        }
        if (accepted.get() + repeat.get() + soldOut.get() + other.get() != USER_COUNT) {
            throw new AssertionError("分类计数与请求总数不一致");
        }
        if (other.get() > 0) {
            throw new AssertionError("存在 " + other + " 个未识别响应或请求异常");
        }
        System.out.println("PASS: 接口受理数未超过库存；请等待异步消费后执行 performance/verify.sql。");
    }

    private static void classify(ObjectMapper mapper, String responseBody,
                                 AtomicInteger accepted, AtomicInteger repeat,
                                 AtomicInteger soldOut, AtomicInteger other) {
        try {
            JsonNode body = mapper.readTree(responseBody);
            String message = body.path("message").asText();
            if (body.isBoolean() && body.asBoolean()) {
                accepted.incrementAndGet();
            } else if (message.contains("重复") || message.contains("参与过")) {
                repeat.incrementAndGet();
            } else if (message.contains("库存不足")) {
                soldOut.incrementAndGet();
            } else {
                other.incrementAndGet();
            }
        } catch (Exception parseFailure) {
            other.incrementAndGet();
        }
    }

    private static long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    private static void register(HttpClient client, ObjectMapper mapper,
                                 String username, String password) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        node.put("nickname", username);
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/api/user/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(node)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("注册失败: " + response.body());
        }
    }

    private static String login(HttpClient client, ObjectMapper mapper,
                                String username, String password) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/api/user/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(node)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String token = extractToken(mapper, response.body());
        if (response.statusCode() >= 400 || token.isBlank()) {
            throw new IllegalStateException("登录失败: " + response.body());
        }
        return token;
    }

    private static String extractToken(ObjectMapper mapper, String responseBody) {
        String trimmedBody = responseBody.trim();
        try {
            JsonNode body = mapper.readTree(trimmedBody);
            return body.isTextual() ? body.asText() : body.path("data").asText();
        } catch (Exception nonJsonResponse) {
            // String 返回值可能由 Spring MVC 以 text/plain 直接输出 JWT。
            return trimmedBody;
        }
    }
}
