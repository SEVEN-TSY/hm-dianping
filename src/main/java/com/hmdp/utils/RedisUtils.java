package com.hmdp.utils;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * @Description TODO
 * @Author sevenxylee
 * @Date 2022/12/20 21:13
 **/
@Slf4j
@Component
public class RedisUtils {


    public static final String LOCK_KEY = "lock:";
    public static final Long LOCK_TTL = 10L;
    public static final Long CACHE_NULL_TTL = 1L;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private static StringRedisTemplate stringRedisTemplate;

    @Resource
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 添加缓存
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param time 缓存逻辑过期时间
     * @param unit 缓存逻辑过期时间单位
     */
    public static void set(String key,Object value,Long time,TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value),time,unit);
    }

    /**
     * 添加缓存，逻辑过期
     *
     * @param key 缓存key
     * @param value 缓存值
     * @param time 缓存逻辑过期时间
     * @param unit 缓存逻辑过期时间单位
     * @param <T> 缓存值类型
     */
    public static<T> void setWithLogicalExpire(String key,T value,Long time,TimeUnit unit){
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));

    }

    /**
     * 获取缓存，缓存穿透、缓存击穿防御
     *
     * @param keyPrefix 缓存key前缀
     * @param id 缓存唯一id
     * @param type 缓存值的实体类型
     * @param dbFallback 无缓存时查询数据库的方法
     * @param time 重建缓存的逻辑过期时间
     * @param unit 重建缓存的逻辑过期时间的单位
     * @param <ID> 缓存id类型
     * @param <R> 返回值类型
     * @return
     */
    public static <ID,R> R get(String keyPrefix, ID id,Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String cache = stringRedisTemplate.opsForValue().get(key);
        R result = null;
        if ("".equals(cache)) {//解决缓存穿透
            log.info("Defence cache penetration. key is : {}", key);
            return null;
        }
        if (cache != null) {//存在缓存，并且不是缓存穿透
            log.info("Redis cache hit. Hot-key is : {}", key);
            return JSON.parseObject(cache, type);
        }
        //不存在缓存，特别针对热点key失效情况
        String lockKey = LOCK_KEY + id;
        try {
            //除第一个获取到互斥锁的线程外都自旋
            do {
            } while (!RedisUtils.tryLock(lockKey));
            String doubleCheck = stringRedisTemplate.opsForValue().get(key);
            if (doubleCheck != null) {//double check
                log.info("Redis cache hit. Hot-key is : {}", key);
                return JSON.parseObject(doubleCheck, type);
            }
            result = dbFallback.apply(id);
            if (result == null) {
                //缓存空值，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            RedisUtils.set(key,result,time,unit);
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            RedisUtils.unLock(lockKey);
        }

        return result;
    }


    /**
     * 获取逻辑过期的热点key缓存
     *
     * @param keyPrefix 缓存key前缀
     * @param id 缓存唯一id
     * @param typeReference 缓存值的复杂类型
     * @param dbFallback 无缓存时查询数据库的方法
     * @param time 重建缓存的逻辑过期时间
     * @param unit 重建缓存的逻辑过期时间的单位
     * @param <ID> 缓存id类型
     * @param <R> 返回值类型
     * @return
     */
    public static <ID,R> R get(String keyPrefix, ID id,TypeReference<RedisData<R>> typeReference, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String cache = stringRedisTemplate.opsForValue().get(key);
        if (cache == null) {//不是热点key
            return null;
        }
        RedisData<R> redisData = JSON.parseObject(cache, typeReference);
        R result= redisData.getData();

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            log.info("Redis cache hit. Hot-key is : {}", key);
            return result;
        }
        String lockKey = LOCK_KEY + id;
        boolean lock = RedisUtils.tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R value = dbFallback.apply(id);
                    setWithLogicalExpire(key,value,time,unit);
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
            redisData= JSON.parseObject(doubleCheck, typeReference);
            result= redisData.getData();

        }
        return result;
    }


    public static boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(flag);
    }

    public static void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
