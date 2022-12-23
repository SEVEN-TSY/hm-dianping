package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Description TODO
 * @Author sevenxylee
 * @Date 2022/12/20 21:13
 **/
@Component
public class RedisUtils {


    private static StringRedisTemplate stringRedisTemplate;

    @Resource
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
    }

    public static boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        if (flag!=null&& flag) {
            return true;
        }
        return false;
    }

    public static void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
