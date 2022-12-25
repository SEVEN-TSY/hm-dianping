package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Description Redis全局唯一id生成器
 * @Author sevenxylee
 * @Date 2022/12/25 16:24
 **/
@Component
@Slf4j
public class RedisIDGenerator {



    private StringRedisTemplate stringRedisTemplate;
    private final static int COUNT_BITS=32;

    public RedisIDGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //时间戳+redis自增
        long timeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        String format = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + format);
        return timeStamp << COUNT_BITS | increment;
    }
}
