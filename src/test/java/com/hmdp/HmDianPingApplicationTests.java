package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;

    @Test
    public void putRedisData2Redis(){
        service.putHotshop2Redis(1L,1L);
    }


}
