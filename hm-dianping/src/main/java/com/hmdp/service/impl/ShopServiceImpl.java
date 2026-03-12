package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 互斥锁方案：防缓存击穿 + 防缓存穿透（二合一）
        // 如果想改成逻辑过期方案，把下面这行换成 queryWithLogicalExpire 即可
        Shop shop = cacheClient.queryWithMutex(
                RedisConstants.CACHE_SHOP_KEY,  // key 前缀
                RedisConstants.LOCK_SHOP_KEY,   // 锁的前缀
                id,                             // 商铺 id
                Shop.class,                     // 反序列化类型
                this::getById,                  // 缓存未命中时查数据库（方法引用）
                RedisConstants.CACHE_SHOP_TTL,  // 缓存过期时长（30分钟）
                TimeUnit.MINUTES                // 时间单位
        );

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        // 1. 先更新数据库 MySQL
        updateById(shop);
        // 2. 再删除 Redis 缓存（双写一致性）
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
