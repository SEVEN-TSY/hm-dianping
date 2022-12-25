package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.ref.ReferenceQueue;
import java.time.LocalDateTime;

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
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        synchronized (userId.toString().intern()){
            return proxy.queryVoucherByUserId(voucherId);
        }
    }

    @Transactional
    public Result queryVoucherByUserId(Long voucherId){
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
