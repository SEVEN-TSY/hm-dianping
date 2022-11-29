package com.hmdp.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Description TODO
 * @Author sevenxylee
 * @Date 2022/11/10 20:41
 **/
public class RefreshTokenExpireTime implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenExpireTime(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token=request.getHeader("authorization");
        if(StringUtils.isBlank(token)){
            return true;
        }
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(key);
        if (user.isEmpty()){
            response.setStatus(401);
            return false;
        }
        UserDTO userDTO = new UserDTO();
        String id = (String) user.get("id");
        String icon = (String) user.get("icon");
        String nickName = (String) user.get("nickName");
        userDTO.setId(Long.valueOf(id));
        userDTO.setIcon(icon);
        userDTO.setNickName(nickName);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
