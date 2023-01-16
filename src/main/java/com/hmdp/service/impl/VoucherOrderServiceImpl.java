package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.RedisSimpleLock;
import com.hmdp.utils.UserHolder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOCK_VOUCHER_SECKILL_TTL;
import static com.hmdp.utils.RedisConstants.ORDER_KEY_PREFIX;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIDGenerator idGenerator;

    @Resource
    private RedisSimpleLock simpleLock;

    @Resource
    private RedissonClient redissonClient;


    @Override
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);

        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("抢购尚未开始！");
        }
        if(endTime.isBefore(LocalDateTime.now())){
            return Result.fail("抢购已结束！");
        }
        if(seckillVoucher.getStock()<1){
            return Result.fail("优惠券抢完了！");
        }
        Long userId = UserHolder.getUser().getId();
        //分布式锁key name = prefix+userId+voucherId
        String lockKey= ORDER_KEY_PREFIX + userId + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean isLock = lock.tryLock();
            if(!isLock){
                return Result.ok("抢购排队中，请勿频繁点击！");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.queryVoucherByUserId(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result<Long> queryVoucherByUserId(Long voucherId){
        Long userId = UserHolder.getUser().getId();
        Long id = idGenerator.nextId(ORDER_KEY_PREFIX);
        //一人一单
        Integer count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count>0){
            return Result.fail("你已经抢过了！");
        }
        //扣减库存
        boolean update = iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!update){
            return Result.fail("优惠券抢完了！");
        }
        //生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1);
        save(voucherOrder);
        return Result.ok(id);
    }
}
