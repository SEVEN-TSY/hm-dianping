package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getShopById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        if(shopCache!=null && !shopCache.isEmpty()){
            log.info("Redis cache hit. key is : {}",key);
            return Result.ok(JSON.parseObject(shopCache,Shop.class));
        }
        Shop shop = getById(id);
        if(shop==null){
            return Result.fail("店铺信息不存在！");
        }
        stringRedisTemplate.opsForValue().set(key,JSON.toJSONString(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }
}
