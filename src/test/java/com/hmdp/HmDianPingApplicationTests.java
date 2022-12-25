package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;

    @Resource
    private RedisIDGenerator redisIDGenerator;

    private ExecutorService executorService=Executors.newFixedThreadPool(100);

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

    @Test
    public void testGenerateRedisId(){
        CountDownLatch countDownLatch=new CountDownLatch(100);
        long start=System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 500; j++) {
                    long id = redisIDGenerator.nextId("order");
                    System.out.println("id = " + id);
                }
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
            long end=System.currentTimeMillis();
            System.out.println("time:"+(end-start));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testGenerateRedisId2(){

        System.out.println("id = " + redisIDGenerator.nextId("order"));
    }


}
