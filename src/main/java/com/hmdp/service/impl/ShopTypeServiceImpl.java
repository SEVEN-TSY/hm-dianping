package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPES_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPES_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Ethan
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPES_KEY;
        Set<String> typeList = stringRedisTemplate.opsForZSet().range(key, 0, 9);
        if (typeList!=null && !typeList.isEmpty()){
            log.info("Redis cache hit. key is : {}", key);
            ArrayList<ShopType> list = new ArrayList<>();
            typeList.forEach(e->{
                list.add(JSON.parseObject(e,ShopType.class));
            });
            return Result.ok(list);
        }
        List<ShopType> queryList = query().orderByDesc("sort").list();
        if(queryList==null){
            return Result.fail("查询商户分类失败！");
        }
        queryList.forEach(e->{
            stringRedisTemplate.opsForZSet().add(key, JSON.toJSONString(e),e.getSort());
        });
        stringRedisTemplate.expire(key,CACHE_SHOP_TYPES_TTL, TimeUnit.MINUTES);

        return Result.ok(queryList);

    }
}
