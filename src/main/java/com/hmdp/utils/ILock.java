package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

/**
 *自定义分布式锁接口
 */
public interface ILock {

    /**
     * 添加分布式锁
     * @param key 锁key名称
     * @param time 过期时间，秒
     * @return 是否加锁成功
     */
    boolean tryLock(String key,Long time);

    /**
     * 释放分布式锁
     * @param key 锁key名称
     */
    void unlock(String key);
}
