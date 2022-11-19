package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.sql.Time;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("phone format wrong : {}",phone);
            return Result.fail("手机号格式错误");
        }
        String numbers = RandomUtil.randomNumbers(6);
        log.info("pass code : {}",numbers);
        //保存到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,numbers,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //模拟实际发送验证码
        log.info("sendCode success");

        return Result.ok();


    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone =loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("phone format wrong : {}",phone);
            return Result.fail("手机号格式错误");
        }
        String trueCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //验证码是否一致
        if(trueCode==null||!trueCode.equals(loginForm.getCode())){
            log.info("access code wrong : {},right code is : {}",loginForm.getCode(),trueCode);
            return Result.fail("验证码错误");
        }
        //用户是否存在
        User user = query().eq("phone", phone).one();

        if(user==null){
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        HashMap<String, String> map = new HashMap<>();
        String token = UUID.randomUUID().toString();
        map.put("id",userDTO.getId().toString());
        map.put("icon",userDTO.getIcon());
        map.put("nickName",userDTO.getNickName());
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+ token,map);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(6));
        save(user);
        log.info("saved new user : {}",user.toString());
        return user;
    }
}
