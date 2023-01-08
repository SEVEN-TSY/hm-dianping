package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Description 基于redis的简单分布式锁
 * @Author sevenxylee
 * @Date 2023/1/7 14:24
 **/
@Component
public class RedisSimpleLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private static final String VAL_PREFIX = UUID.randomUUID()+":";

    public RedisSimpleLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(String key, Long time) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, VAL_PREFIX+Thread.currentThread().getId(), time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unlock(String key) {
        String value = VAL_PREFIX + Thread.currentThread().getId();
        String checkValue = stringRedisTemplate.opsForValue().get(key);
        if (value.equals(checkValue)) {
            stringRedisTemplate.delete(key);
        }
    }
}
