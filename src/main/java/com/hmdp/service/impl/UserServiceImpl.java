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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Random;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("phone format wrong : {}",phone);
            return Result.fail("手机号格式错误");
        }
        String numbers = RandomUtil.randomNumbers(6);
        log.info("pass code : {}",numbers);
        session.setAttribute("code",numbers);
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
        Object trueCode = session.getAttribute("code");
        //验证码是否一致
        if(trueCode==null||!trueCode.toString().equals(loginForm.getCode())){
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
        session.setAttribute("loginUser",userDTO );

        return Result.ok();
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
