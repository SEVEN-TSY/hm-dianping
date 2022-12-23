package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ethan
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result getShopById(Long id) {
        //Shop shop = defenceCachePenetration(id);
        Shop shop = defenceCacheBreakDownV2(id);
        if (shop == null) {
            return Result.fail("店铺信息不存在！");
        }

        return Result.ok(shop);
    }

    private Shop defenceCachePenetration(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        if ("".equals(shopCache)) {//解决缓存穿透
            log.info("Defence cache penetration. key is : {}", key);
            return null;
        }
        if (shopCache != null) {//存在缓存，并且不是缓存穿透
            log.info("Redis cache hit. key is : {}", key);
            return JSON.parseObject(shopCache, Shop.class);
        }
        //不存在缓存
        Shop shop = getById(id);
        if (shop == null) {
            //缓存空值，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private Shop defenceCacheBreakDownV1(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        Shop shop = null;
        if ("".equals(shopCache)) {//解决缓存穿透
            log.info("Defence cache penetration. key is : {}", key);
            return null;
        }
        if (shopCache != null) {//存在缓存，并且不是缓存穿透
            log.info("Redis cache hit. Hot-key is : {}", key);
            return JSON.parseObject(shopCache, Shop.class);
        }
        //不存在缓存，特别针对热点key失效情况
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            //除第一个获取到互斥锁的线程外都自旋
            do {
            } while (!RedisUtils.tryLock(lockKey));
            String doubleCheck = stringRedisTemplate.opsForValue().get(key);
            if (doubleCheck != null) {//double check
                log.info("Redis cache hit. Hot-key is : {}", key);
                return JSON.parseObject(doubleCheck, Shop.class);
            }
            Thread.sleep(200);//模拟重建热点缓存延时
            shop = getById(id);
            if (shop == null) {
                //缓存空值，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            RedisUtils.unLock(lockKey);
        }

        return shop;
    }

    private Shop defenceCacheBreakDownV2(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        Shop shop = new Shop();
        if (shopCache == null) {//不是热点key
            return null;
        }
        RedisData<Shop> redisData = JSON.parseObject(shopCache, new TypeReference<RedisData<Shop>>(){});


        shop= redisData.getData();

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            log.info("Redis cache hit. Hot-key is : {}", key);
            return shop;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = RedisUtils.tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    putHotshop2Redis(id, CACHE_SHOP_TTL);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    RedisUtils.unLock(lockKey);
                }
            });
        }
        String doubleCheck = stringRedisTemplate.opsForValue().get(key);
        if (doubleCheck != null) {//double check
            log.info("Redis cache hit. Hot-key is : {}", key);
            redisData= JSON.parseObject(doubleCheck, new TypeReference<RedisData<Shop>>(){});
            shop= redisData.getData();

        }
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        //校验shop id
        if (shop.getId() == null) {
            return Result.fail("店铺信息不存在！");
        }
        //先更新数据库
        boolean b = updateById(shop);
        //删除缓存
        if (b) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
            log.info("Shop info updated. Redis cache {} deleted", CACHE_SHOP_KEY + shop.getId());
        }
        return Result.ok();
    }


    public void putHotshop2Redis(Long id, Long expireTime) {
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(getById(id));
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONObject.toJSONString(redisData));
    }
}
