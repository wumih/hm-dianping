package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import cn.hutool.core.bean.copier.CopyOptions;
import java.util.HashMap;
import java.util.Map;
import cn.hutool.core.lang.UUID;

import static com.hmdp.utils.RedisConstants.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import com.hmdp.utils.UserHolder;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserInfoService userInfoService;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3. 符合，生成验证码（6位数字）
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到 session
//        session.setAttribute("code", code);
        // 4.保存验证码到 redis  (!!! 重点：修改成了这句 !!!)
        // 解释：
        // LOGIN_CODE_KEY 应该是一个常量，值大概是 "login:code:"，加上 phone 就成了唯一 key
        // code 是验证码本身
        // LOGIN_CODE_TTL 是有效期时间，比如设置为 2
        // TimeUnit.MINUTES 表示单位是 分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5. 发送验证码（模拟，打印日志）
        // 改之后（验证码直接附在响应数据里返回）：
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok(code);  // ← 把 code 字符串作为 data 带出去
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        User user;
        String password = loginForm.getPassword();
        String code = loginForm.getCode();

        if (cn.hutool.core.util.StrUtil.isBlank(code) && cn.hutool.core.util.StrUtil.isNotBlank(password)) {
            // ========== 密码登录分支 ==========
            // 2a. 根据手机号查询用户
            user = query().eq("phone", phone).one();
            if (user == null) {
                return Result.fail("该手机号尚未注册，请先通过验证码登录完成注册！");
            }
            // 2b. 验证密码
            if (cn.hutool.core.util.StrUtil.isBlank(user.getPassword())) {
                return Result.fail("您尚未设置密码，请使用验证码登录后再设置密码！");
            }
            if (!PasswordEncoder.matches(user.getPassword(), password)) {
                return Result.fail("密码错误！");
            }
        } else {
            // ========== 验证码登录分支（原逻辑保留）==========
            // 2b. 从 Redis 获取验证码并校验
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            if (cacheCode == null || !cacheCode.equals(code)) {
                return Result.fail("验证码错误");
            }
            // 3. 查询用户，不存在则自动注册
            user = query().eq("phone", phone).one();
            if (user == null) {
                user = createUserWithPhone(phone);
            }
        }

        // 4. 登录成功，生成 Token 并存入 Redis
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result setPassword(String phone, String newPassword) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 校验密码长度（至少 6 位）
        if (cn.hutool.core.util.StrUtil.isBlank(newPassword) || newPassword.length() < 6) {
            return Result.fail("密码长度不能少于6位！");
        }
        // 3. 查询用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        // 4. 加密并更新密码
        String encodedPassword = PasswordEncoder.encode(newPassword);
        boolean success = update().eq("phone", phone).set("password", encodedPassword).update();
        if (!success) {
            return Result.fail("密码设置失败，请重试！");
        }
        return Result.ok();
    }

    @Override
    public Result logout(String token) {
        if (cn.hutool.core.util.StrUtil.isBlank(token)) {
            return Result.ok();
        }
        // 1. 删除 Redis 中的用户信息
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);

        // 2. 移除 ThreadLocal 中的用户信息，安全解绑线程
        UserHolder.removeUser();

        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有签到记录，返回的是一个十进制数字 BITFIELD key GET u{dayOfMonth} 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result updateMe(User user, String token) {
        // 1. 从 ThreadLocal 取当前登录用户的 ID
        Long userId = UserHolder.getUser().getId();
        // 2. 只更新允许的字段：nickName 和 icon，并锁定操作人为当前用户
        User update = new User();
        update.setId(userId);
        if (cn.hutool.core.util.StrUtil.isNotBlank(user.getNickName())) {
            update.setNickName(user.getNickName());
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(user.getIcon())) {
            update.setIcon(user.getIcon());
        }
        // 3. 更新 MySQL
        boolean success = updateById(update);
        if (!success) {
            return Result.fail("修改失败，请重试！");
        }
        // 4. 同步更新 Redis 中的用户 Hash，使 /user/me 立即返回新数据
        //    Redis Key 格式：login:token:{token}
        String tokenKey = LOGIN_USER_KEY + token;
        if (cn.hutool.core.util.StrUtil.isNotBlank(user.getNickName())) {
            stringRedisTemplate.opsForHash().put(tokenKey, "nickName", user.getNickName());
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(user.getIcon())) {
            stringRedisTemplate.opsForHash().put(tokenKey, "icon", user.getIcon());
        }
        return Result.ok();
    }

    @Override
    public Result updateUserInfo(UserInfo userInfo) {
        // 1. 从 ThreadLocal 获取当前登录用户 ID
        Long userId = UserHolder.getUser().getId();
        // 2. 将 userId 设入实体（tb_user_info 的主键是 user_id，必须手动设置）
        userInfo.setUserId(userId);
        // 3. saveOrUpdate：若 user_id 已存在则 UPDATE，不存在则 INSERT
        //    解决了新注册用户没有 user_info 记录的问题
        boolean success = userInfoService.saveOrUpdate(userInfo);
        return success ? Result.ok() : Result.fail("修改失败，请重试！");
    }

    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2. 保存用户
        save(user);
        return user;
    }
}
