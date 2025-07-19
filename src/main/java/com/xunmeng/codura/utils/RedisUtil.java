package com.xunmeng.codura.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisUtil {
    private static final String HOST = "localhost"; // 按需修改
    private static final int PORT = 6379;
    private static final String PASSWORD = "123456";

    private static final JedisPool pool;

    static {
        pool = new JedisPool(new JedisPoolConfig(), HOST, PORT, 2000, PASSWORD);
        // 测试连接
        try (Jedis jedis = pool.getResource()) {
            String response = jedis.ping();
            if ("PONG".equalsIgnoreCase(response)) {
                System.out.println("✅ Redis 连接成功！");
            } else {
                System.err.println("⚠ Redis 连接失败，ping 返回: " + response);
            }
        } catch (Exception e) {
            System.err.println("❌ Redis 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    public static void set(String key, String value, int ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    public static void del(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }
}
