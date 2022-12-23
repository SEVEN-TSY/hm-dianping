package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
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
        //Shop shop= RedisUtils.get(CACHE_SHOP_KEY,id,new TypeReference<RedisData<Shop>>(){}, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        Shop shop = RedisUtils.get(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺信息不存在！");
        }

        return Result.ok(shop);
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
