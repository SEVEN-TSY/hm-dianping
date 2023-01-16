--KEYS[1]指分布式锁id
--ARGV[1]指实际运行的线程锁值
if(redis.call('GET', KEYS[1]) == ARGV[1]) then
    return redis.call('DEL',KEYS[1])
end
return 0