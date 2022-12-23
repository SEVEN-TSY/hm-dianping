package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;

    @Test
    public void putRedisData2Redis(){
        service.putHotshop2Redis(1L,1L);
    }

    @Test
    public void putRedisData2Redis2(){
        long id = 1L;
        Shop shop = service.getById(id);
        RedisUtils.setWithLogicalExpire(CACHE_SHOP_KEY+id,shop,1L, TimeUnit.SECONDS);
    }


}
