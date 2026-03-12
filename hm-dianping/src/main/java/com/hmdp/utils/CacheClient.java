package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于 StringRedisTemplate 封装的通用缓存工具类
 * 提供 4 大核心方法，覆盖缓存穿透和缓存击穿的防御场景
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 异步重建缓存的专属线程池（固定 10 条线程）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 通过构造函数注入 StringRedisTemplate（Spring 推荐的注入方式）
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 方法 1：普通存入（带 TTL） ====================

    /**
     * 将任意 Java 对象序列化为 JSON 并存入 Redis，同时设置 TTL 过期时间
     *
     * @param key   Redis 中的 key
     * @param value 要存入的 Java 对象（任意类型）
     * @param time  过期时长（数值）
     * @param unit  过期时长的时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // ==================== 方法 2：逻辑过期存入（用于预热热点数据） ====================

    /**
     * 将任意 Java 对象序列化为 JSON，附带逻辑过期时间一起存入 Redis
     * 注意：不设置 Redis 的 TTL（key 永不自动过期），由代码逻辑判断是否需要刷新
     * 通常在热点活动开始前，提前由运营人员或定时任务调用此方法
     *
     * @param key   Redis 中的 key
     * @param value 要存入的 Java 对象（任意类型）
     * @param time  逻辑过期时长（数值）
     * @param unit  逻辑过期时长的时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 封装进 RedisData，附带逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 把传入的时间统一转换为秒，再加到当前时间上
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 存入 Redis（不设 TTL，永不过期）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ==================== 方法 3：防缓存穿透查询（空值哨兵方案） ====================

    /**
     * 根据指定的 key 查询缓存，如果缓存未命中则通过 dbFallback 函数查询数据库
     * 利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix  key 的前缀（如 "cache:shop:"）
     * @param id         业务 id（如商铺 id）
     * @param type       返回值的 Class 类型（如 Shop.class）
     * @param dbFallback 缓存未命中时的数据库查询逻辑（函数式接口，调用者传入 lambda）
     * @param time       缓存过期时长（数值）
     * @param unit       缓存过期时长的时间单位
     * @param <R>        返回值类型（如 Shop）
     * @param <ID>       id 的类型（如 Long）
     * @return 查询到的对象，如果不存在则返回 null
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;

        // 1. 从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存命中且有真实数据，直接反序列化返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 3. 命中的是空字符串哨兵（防穿透），说明数据库里也没有
        if (json != null) {
            return null;
        }

        // 4. 缓存未命中（json == null），查数据库
        R result = dbFallback.apply(id);

        // 5. 数据库也查不到，写入空值哨兵防穿透（短 TTL = 2 分钟）
        if (result == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 数据库查到了，写入 Redis 缓存（带 TTL 兜底）
        this.set(key, result, time, unit);

        return result;
    }

    // ==================== 方法 4：防缓存击穿查询（逻辑过期方案） ====================

    /**
     * 根据指定的 key 查询缓存，利用逻辑过期解决缓存击穿问题
     * 特点：永不让用户等待，过期了也先返回旧数据，后台异步重建缓存
     * 前提条件：热点数据必须提前通过 setWithLogicalExpire 预热写入 Redis
     *
     * @param keyPrefix  key 的前缀（如 "cache:shop:"）
     * @param lockPrefix 锁的前缀（如 "lock:shop:"）
     * @param id         业务 id（如商铺 id）
     * @param type       返回值的 Class 类型（如 Shop.class）
     * @param dbFallback 缓存重建时的数据库查询逻辑
     * @param time       逻辑过期时长（数值）
     * @param unit       逻辑过期时长的时间单位
     * @param <R>        返回值类型
     * @param <ID>       id 的类型
     * @return 查询到的对象（可能是旧数据），如果 key 不存在则返回 null
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, String lockPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;

        // 1. 从 Redis 查询
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 未命中，说明不是预热过的热点数据，直接返回 null
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 命中，反序列化为 RedisData
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R result = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断逻辑过期时间
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1 未过期，数据新鲜，直接返回
            return result;
        }

        // 4.2 已过期，需要缓存重建
        String lockKey = lockPrefix + id;

        // 5. 尝试获取互斥锁
        boolean isLock = tryLock(lockKey);

        // 6. 获取锁成功，开启后台线程异步重建
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库
                    R newResult = dbFallback.apply(id);
                    // 将新数据连同新的逻辑过期时间写回 Redis
                    this.setWithLogicalExpire(key, newResult, time, unit);
                } catch (Exception e) {
                    log.error("缓存重建异常", e);
                } finally {
                    // 一定要释放锁
                    unlock(lockKey);
                }
            });
        }

        // 7. 无论是否获取到锁，都立刻返回旧数据（不让用户等待！）
        return result;
    }

    // ==================== 方法 5：防缓存击穿查询（互斥锁方案） ====================

    /**
     * 根据指定的 key 查询缓存，利用互斥锁解决缓存击穿问题
     * 特点：后来的线程阻塞等待，保证数据强一致性
     * 同时兼顾防缓存穿透（空值写入）
     *
     * @param keyPrefix  key 的前缀（如 "cache:shop:"）
     * @param lockPrefix 锁的前缀（如 "lock:shop:"）
     * @param id         业务 id（如商铺 id）
     * @param type       返回值的 Class 类型（如 Shop.class）
     * @param dbFallback 缓存未命中时的数据库查询逻辑
     * @param time       缓存过期时长（数值）
     * @param unit       缓存过期时长的时间单位
     * @param <R>        返回值类型
     * @param <ID>       id 的类型
     * @return 查询到的对象，如果不存在则返回 null
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, String lockPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;

        // 1. 从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存命中且有真实数据，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 3. 命中的是空字符串哨兵（防穿透），返回 null
        if (json != null) {
            return null;
        }

        // 4. 缓存未命中，开始互斥锁重建流程
        String lockKey = lockPrefix + id;
        R result = null;
        try {
            // 4.1 尝试获取互斥锁
            boolean isLock = tryLock(lockKey);

            // 4.2 获取锁失败，休眠后递归重试（包含 Double Check）
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, lockPrefix, id, type, dbFallback, time, unit);
            }

            // 4.3 获取锁成功，Double Check：再查一次 Redis
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }

            // 4.4 确认 Redis 没有，查数据库
            result = dbFallback.apply(id);

            // 5. 数据库也查不到，写入空值哨兵防穿透
            if (result == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6. 数据库查到了，写入 Redis 缓存
            this.set(key, result, time, unit);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 无论成功还是异常，都必须释放锁
            unlock(lockKey);
        }

        return result;
    }

    // ==================== 锁操作（私有工具方法） ====================

    /**
     * 尝试获取互斥锁（基于 Redis SETNX 实现）
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
